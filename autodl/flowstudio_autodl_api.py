import argparse
import os
import re
import shutil
import subprocess
import threading
from pathlib import Path
from typing import Dict, Optional, Set, Tuple

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse
import uvicorn

app = FastAPI(title="FlowStudio AutoDL Wrapper")

BASE_DIR = Path(__file__).resolve().parent
RUNTIME_DIR = BASE_DIR / "flowstudio_runtime"
USERS_DIR = RUNTIME_DIR / "users"

USERNAME_RE = re.compile(r"^[a-z0-9_-]{3,32}$")
TASK_ID_RE = re.compile(r"^task-[a-f0-9-]{8,36}$")
FILE_NAME_RE = re.compile(r"^[A-Za-z0-9._-]+$")
TOKEN_HEADER = "X-FlowStudio-Token"

ACTIVE_LOCK = threading.RLock()
ACTIVE_PROCESSES: Dict[Tuple[str, str], subprocess.Popen] = {}
USER_GENERATIONS: Dict[str, int] = {}
TASK_GENERATIONS: Dict[Tuple[str, str], int] = {}
DELETING_USERS: Set[str] = set()
DELETING_TASKS: Set[Tuple[str, str]] = set()


def _require_token(received: Optional[str]) -> None:
    expected = os.getenv("FLOWSTUDIO_AUTODL_TOKEN", "")
    if expected and received != expected:
        raise HTTPException(status_code=401, detail="Invalid internal token.")


def _safe_username(value: str) -> str:
    username = (value or "").strip().lower()
    if not USERNAME_RE.fullmatch(username):
        raise HTTPException(status_code=400, detail="Invalid username.")
    return username


def _safe_task_id(value: str) -> str:
    task_id = (value or "").strip().lower()
    if not TASK_ID_RE.fullmatch(task_id):
        raise HTTPException(status_code=400, detail="Invalid taskId.")
    return task_id


def _safe_file_name(value: str) -> str:
    if not FILE_NAME_RE.fullmatch(value or ""):
        raise HTTPException(status_code=400, detail="Invalid file name.")
    return value


def _user_root(username: str) -> Path:
    users_root = USERS_DIR.resolve()
    root = (USERS_DIR / username).resolve()
    if users_root not in root.parents:
        raise HTTPException(status_code=400, detail="Invalid user path.")
    return root


def _task_dir(username: str, task_id: str) -> Path:
    user_root = _user_root(username)
    task_root = (user_root / "tasks" / task_id).resolve()
    if user_root not in task_root.parents:
        raise HTTPException(status_code=400, detail="Invalid task path.")
    return task_root


def _user_generation(username: str) -> int:
    return USER_GENERATIONS.get(username, 0)


def _task_generation(key: Tuple[str, str]) -> int:
    return TASK_GENERATIONS.get(key, 0)


def _ensure_generation(
    username: str,
    task_id: str,
    expected_user_generation: int,
    expected_task_generation: int,
) -> None:
    key = (username, task_id)
    with ACTIVE_LOCK:
        if (
            username in DELETING_USERS
            or key in DELETING_TASKS
            or _user_generation(username) != expected_user_generation
            or _task_generation(key) != expected_task_generation
        ):
            raise HTTPException(
                status_code=409,
                detail="The task or user was deleted while processing.",
            )


def _terminate_processes(processes) -> None:
    for process in processes:
        if process.poll() is None:
            process.terminate()

    for process in processes:
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "runtimeDir": str(RUNTIME_DIR),
        "usersDir": str(USERS_DIR),
    }


@app.post("/edit")
async def edit(
    username: str = Form(...),
    taskId: str = Form(...),
    sourcePrompt: str = Form(""),
    targetPrompt: str = Form(...),
    targetWord: str = Form(...),
    video: UploadFile = File(...),
    mask: UploadFile = File(...),
    x_flowstudio_token: Optional[str] = Header(
        default=None, alias=TOKEN_HEADER
    ),
):
    _require_token(x_flowstudio_token)
    username = _safe_username(username)
    task_id = _safe_task_id(taskId)
    key = (username, task_id)

    with ACTIVE_LOCK:
        if username in DELETING_USERS or key in DELETING_TASKS:
            raise HTTPException(
                status_code=409, detail="This task or account is being deleted."
            )
        user_generation = _user_generation(username)
        task_generation = _task_generation(key)

    task_dir = _task_dir(username, task_id)

    video_suffix = Path(video.filename or "input.mp4").suffix.lower()
    if video_suffix not in {".mp4", ".mov", ".webm"}:
        raise HTTPException(status_code=400, detail="Unsupported video type.")

    mask_suffix = Path(mask.filename or "mask.png").suffix.lower()
    if mask_suffix not in {".png", ".jpg", ".jpeg"}:
        raise HTTPException(status_code=400, detail="Unsupported mask type.")

    video_path = task_dir / f"input{video_suffix}"
    mask_path = task_dir / f"mask{mask_suffix}"
    output_path = task_dir / "result.mp4"
    log_path = task_dir / "edit.log"

    try:
        task_dir.mkdir(parents=True, exist_ok=True)
        with video_path.open("wb") as file_handle:
            shutil.copyfileobj(video.file, file_handle)
        with mask_path.open("wb") as file_handle:
            shutil.copyfileobj(mask.file, file_handle)

        _ensure_generation(
            username,
            task_id,
            user_generation,
            task_generation,
        )
    except Exception:
        if task_dir.exists():
            shutil.rmtree(task_dir, ignore_errors=True)
        raise
    finally:
        await video.close()
        await mask.close()

    edit_py = BASE_DIR / "edit.py"
    if not edit_py.exists():
        _ensure_generation(
            username,
            task_id,
            user_generation,
            task_generation,
        )
        shutil.copyfile(video_path, output_path)
        return {
            "success": True,
            "message": (
                "edit.py not found; copied input video as development fallback."
            ),
            "resultPath": f"/files/{username}/{task_id}/result.mp4",
        }

    command = [
        "python",
        str(edit_py),
        "--video_path",
        str(video_path),
        "--output_path",
        str(output_path),
        "--src_prompt",
        sourcePrompt,
        "--tar_prompt",
        targetPrompt,
        "--mask_path",
        str(mask_path),
        "--target_word",
        targetWord,
    ]

    with log_path.open("wb") as log_file:
        with ACTIVE_LOCK:
            _ensure_generation(
                username,
                task_id,
                user_generation,
                task_generation,
            )
            process = subprocess.Popen(
                command,
                cwd=str(BASE_DIR),
                stdout=log_file,
                stderr=subprocess.STDOUT,
            )
            ACTIVE_PROCESSES[key] = process

        try:
            return_code = process.wait()
        finally:
            with ACTIVE_LOCK:
                if ACTIVE_PROCESSES.get(key) is process:
                    ACTIVE_PROCESSES.pop(key, None)

    _ensure_generation(
        username,
        task_id,
        user_generation,
        task_generation,
    )

    if return_code != 0:
        return {
            "success": False,
            "message": (
                f"edit.py failed with exit code {return_code}. "
                f"See {log_path.name}."
            ),
            "resultPath": "",
        }

    if not output_path.exists():
        return {
            "success": False,
            "message": "edit.py finished but result.mp4 was not created.",
            "resultPath": "",
        }

    return {
        "success": True,
        "message": "AutoDL edit success.",
        "resultPath": f"/files/{username}/{task_id}/result.mp4",
    }


@app.delete("/internal/users/{username}/tasks/{task_id}")
def delete_task(
    username: str,
    task_id: str,
    x_flowstudio_token: Optional[str] = Header(
        default=None, alias=TOKEN_HEADER
    ),
):
    _require_token(x_flowstudio_token)
    username = _safe_username(username)
    task_id = _safe_task_id(task_id)
    key = (username, task_id)

    with ACTIVE_LOCK:
        DELETING_TASKS.add(key)
        TASK_GENERATIONS[key] = _task_generation(key) + 1
        process = ACTIVE_PROCESSES.get(key)

    try:
        if process is not None:
            _terminate_processes([process])

        with ACTIVE_LOCK:
            ACTIVE_PROCESSES.pop(key, None)

        task_root = _task_dir(username, task_id)
        if task_root.exists():
            shutil.rmtree(task_root)
    finally:
        with ACTIVE_LOCK:
            DELETING_TASKS.discard(key)

    return {
        "success": True,
        "message": f"Deleted AutoDL task {task_id} for {username}.",
    }


@app.delete("/internal/users/{username}")
def delete_user(
    username: str,
    x_flowstudio_token: Optional[str] = Header(
        default=None, alias=TOKEN_HEADER
    ),
):
    _require_token(x_flowstudio_token)
    username = _safe_username(username)

    with ACTIVE_LOCK:
        DELETING_USERS.add(username)
        USER_GENERATIONS[username] = _user_generation(username) + 1
        processes = [
            process
            for (owner, _), process in ACTIVE_PROCESSES.items()
            if owner == username
        ]
        for key in list(TASK_GENERATIONS):
            if key[0] == username:
                TASK_GENERATIONS[key] = _task_generation(key) + 1

    try:
        _terminate_processes(processes)

        with ACTIVE_LOCK:
            for key in list(ACTIVE_PROCESSES):
                if key[0] == username:
                    ACTIVE_PROCESSES.pop(key, None)

        user_root = _user_root(username)
        if user_root.exists():
            shutil.rmtree(user_root)
    finally:
        with ACTIVE_LOCK:
            DELETING_USERS.discard(username)
            DELETING_TASKS.difference_update(
                key for key in DELETING_TASKS if key[0] == username
            )

    return {
        "success": True,
        "message": f"Deleted AutoDL data for {username}.",
    }


@app.get("/files/{username}/{task_id}/{file_name}")
def file(
    username: str,
    task_id: str,
    file_name: str,
    x_flowstudio_token: Optional[str] = Header(
        default=None, alias=TOKEN_HEADER
    ),
):
    _require_token(x_flowstudio_token)
    username = _safe_username(username)
    task_id = _safe_task_id(task_id)
    file_name = _safe_file_name(file_name)

    task_root = _task_dir(username, task_id)
    path = (task_root / file_name).resolve()
    if (
        task_root not in path.parents
        or not path.exists()
        or not path.is_file()
    ):
        raise HTTPException(status_code=404, detail="File not found.")
    return FileResponse(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    uvicorn.run(app, host=args.host, port=args.port)


if __name__ == "__main__":
    main()
