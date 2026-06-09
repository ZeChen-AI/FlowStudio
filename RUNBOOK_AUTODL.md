# AutoDL Runbook

Current recommended development mode:

```bash
cd backend
mvn spring-boot:run
```

This starts FlowStudio in mock mode:

```text
flowstudio.mock-runner=true
```

The UI, login, registration, logout, account deletion, task submission, polling, mask upload/drawing, and result preview can all be tested without FlowAnchor.

## Required Login Demo Assets

Place the login background video pairs in both asset locations:

```text
assets/
backend/src/main/resources/static/assets/
```

Required files:

```text
9_src.mp4
9_ours.mp4
8_src.mp4
8_ours.mp4
7_src.mp4
7_ours.mp4
```

The login gate uses the `_src` videos as the source layer and the `_ours` videos as the FlowStudio edited layer.

## Local Mock Mode

Start the backend:

```bash
cd backend
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

In mock mode, `MockTaskRunner` writes:

```text
dataset/<username>/tasks/<taskId>/result.mp4
```

by copying the uploaded input video. This verifies the full frontend-to-backend task lifecycle without running a real video editing model.

## Later: Real AutoDL Mode

1. Copy the AutoDL wrapper files next to the real `edit.py`:

   ```text
   autodl/flowstudio_autodl_api.py
   autodl/requirements.txt
   ```

2. Install dependencies and start the wrapper:

   ```bash
   pip install -r requirements.txt
   python flowstudio_autodl_api.py --host 0.0.0.0 --port 8000
   ```

3. Expose or forward the wrapper port.

4. Start the Java backend with mock mode disabled:

   ```bash
   cd backend
   FLOWSTUDIO_MOCK_RUNNER=false \
   AUTODL_BASE_URL=http://127.0.0.1:8000 \
   mvn spring-boot:run
   ```

The Java backend calls:

```text
POST <AUTODL_BASE_URL>/edit
```

with multipart fields:

```text
taskId
sourcePrompt
targetPrompt
targetWord
video
mask
```

The wrapper saves the request into:

```text
autodl/flowstudio_runtime/tasks/<taskId>/
```

and prepares:

```text
input.mp4
mask.png
result.mp4
```

Then it calls:

```bash
python edit.py \
  --video_path input.mp4 \
  --output_path result.mp4 \
  --src_prompt "..." \
  --tar_prompt "..." \
  --mask_path mask.png \
  --target_word "rose"
```

If the real `edit.py` expects a mask directory rather than a single mask image, update only `flowstudio_autodl_api.py` to convert `mask.png` into the format required by the model. The frontend API and Java `TaskRunner` abstraction can remain unchanged.

## Backend Environment Variables

```bash
PORT=8080
FLOWSTUDIO_DATASET_DIR=dataset
FLOWSTUDIO_RUNTIME_DIR=runtime
FLOWSTUDIO_MOCK_RUNNER=true
AUTODL_BASE_URL=
FLOWSTUDIO_RUNNER_TIMEOUT_SECONDS=1800
```

For MySQL user storage:

```bash
FLOWSTUDIO_MYSQL_ENABLED=true \
FLOWSTUDIO_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/flowstudio?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true' \
FLOWSTUDIO_MYSQL_USERNAME='root' \
FLOWSTUDIO_MYSQL_PASSWORD='YOUR_PASSWORD' \
mvn spring-boot:run
```

## Common AutoDL Notes

- Keep `flowstudio.mock-runner=true` until the login, task submission, and result preview flow works locally.
- Use an absolute reachable `AUTODL_BASE_URL` when the Java backend and AutoDL wrapper run on different machines.
- If Maven reports duplicate Java classes, remove `.ipynb_checkpoints/` folders from `src/main/java`.
- Keep generated `target/` files out of source control.
- Keep user data under `dataset/` out of source control unless a tiny demo fixture is intentionally needed.
