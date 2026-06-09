import argparse
import shutil
import subprocess
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import FileResponse
import uvicorn

app = FastAPI(title="FlowStudio AutoDL Wrapper")

BASE_DIR = Path(__file__).resolve().parent
RUNTIME_DIR = BASE_DIR / "flowstudio_runtime"
TASKS_DIR = RUNTIME_DIR / "tasks"


@app.get("/health")
def health():
    return {"status": "ok", "runtimeDir": str(RUNTIME_DIR)}


@app.post("/edit")
async def edit(
    taskId: str = Form(...),
    sourcePrompt: str = Form(""),
    targetPrompt: str = Form(...),
    targetWord: str = Form(...),
    video: UploadFile = File(...),
    mask: UploadFile = File(...),
):
    task_dir = TASKS_DIR / taskId
    task_dir.mkdir(parents=True, exist_ok=True)

    video_path = task_dir / "input.mp4"
    mask_path = task_dir / "mask.png"
    output_path = task_dir / "result.mp4"

    with video_path.open("wb") as f:
        shutil.copyfileobj(video.file, f)

    with mask_path.open("wb") as f:
        shutil.copyfileobj(mask.file, f)

    edit_py = BASE_DIR / "edit.py"
    if not edit_py.exists():
        # Development fallback: copy input to result.
        shutil.copyfile(video_path, output_path)
        return {
            "success": True,
            "message": "edit.py not found; copied input video as development fallback.",
            "resultPath": f"/files/{taskId}/result.mp4",
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

    try:
        subprocess.run(command, check=True, cwd=str(BASE_DIR))
    except subprocess.CalledProcessError as error:
        return {
            "success": False,
            "message": f"edit.py failed with exit code {error.returncode}",
            "resultPath": "",
        }

    if not output_path.exists():
        return {"success": False, "message": "edit.py finished but result.mp4 was not created.", "resultPath": ""}

    return {"success": True, "message": "AutoDL edit success.", "resultPath": f"/files/{taskId}/result.mp4"}


@app.get("/files/{task_id}/{file_name}")
def file(task_id: str, file_name: str):
    path = (TASKS_DIR / task_id / file_name).resolve()
    task_root = (TASKS_DIR / task_id).resolve()
    if not str(path).startswith(str(task_root)) or not path.exists():
        return {"success": False, "message": "File not found."}
    return FileResponse(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    uvicorn.run(app, host=args.host, port=args.port)


if __name__ == "__main__":
    main()
