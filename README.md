# FlowStudio

FlowStudio is a classroom-ready AI video editing demo. The current project contains a native HTML/CSS/JavaScript frontend, a Java Spring Boot orchestration backend, local or MySQL-backed user authentication, per-user task storage, and an optional AutoDL FastAPI wrapper around a future `edit.py` video editing script.

The current login gate uses a gray-black video comparison landing page. It shows original `source` videos and edited `FlowStudio` videos from `assets/`, supports a draggable vertical comparison bar, and provides left/right switching between three demo pairs before opening the login/register modal.

## Project Structure

```text
FlowStudio/
├── assets/
│   ├── edit-canvas.png
│   ├── login-reference.jpg
│   ├── studio-hero.png
│   ├── 9_src.mp4
│   ├── 9_ours.mp4
│   ├── 8_src.mp4
│   ├── 8_ours.mp4
│   ├── 7_src.mp4
│   └── 7_ours.mp4
├── autodl/
│   ├── flowstudio_autodl_api.py
│   └── requirements.txt
├── backend/
│   ├── dataset/
│   │   └── users.json
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/studio/flow/
│       │   │   ├── FlowStudioApplication.java
│       │   │   ├── config/
│       │   │   │   ├── AppConfig.java
│       │   │   │   ├── CorsConfig.java
│       │   │   │   ├── FlowStudioProperties.java
│       │   │   │   └── UserStoreConfig.java
│       │   │   ├── controller/
│       │   │   │   ├── ApiExceptionHandler.java
│       │   │   │   ├── AuthController.java
│       │   │   │   ├── FrontendController.java
│       │   │   │   ├── HealthController.java
│       │   │   │   ├── TaskController.java
│       │   │   │   └── UnauthorizedException.java
│       │   │   ├── dto/
│       │   │   │   ├── AuthRequest.java
│       │   │   │   ├── AuthResponse.java
│       │   │   │   ├── HealthResponse.java
│       │   │   │   ├── ResultResponse.java
│       │   │   │   └── TaskResponse.java
│       │   │   ├── model/
│       │   │   │   ├── EditTask.java
│       │   │   │   ├── TaskStatus.java
│       │   │   │   └── UserRecord.java
│       │   │   ├── runner/
│       │   │   │   ├── AutodlTaskRunner.java
│       │   │   │   ├── MockTaskRunner.java
│       │   │   │   ├── RunnerResult.java
│       │   │   │   └── TaskRunner.java
│       │   │   └── service/
│       │   │       ├── LocalUserStore.java
│       │   │       ├── MysqlUserStore.java
│       │   │       ├── PasswordHasher.java
│       │   │       ├── TaskService.java
│       │   │       ├── UserService.java
│       │   │       └── UserStore.java
│       │   └── resources/
│       │       ├── application.properties
│       │       └── static/
│       │           ├── index.html
│       │           ├── script.js
│       │           ├── styles.css
│       │           └── assets/
│       └── test/java/studio/flow/
│           └── FlowStudioApplicationTests.java
├── index.html
├── script.js
├── styles.css
├── README.md
├── ITERATION_STORY.md
└── RUNBOOK_AUTODL.md
```

## Frontend

The frontend is implemented with native HTML, CSS, and JavaScript.

Open `index.html` directly for visual inspection, or run it through the Java backend for same-origin API access.

The login gate supports:

- Gray-black full-screen landing page.
- Top-left `FlowStudio` brand text.
- Top-right `Log in` button.
- Source/FlowStudio video comparison using `assets/9_src.mp4`, `assets/9_ours.mp4`, `assets/8_src.mp4`, `assets/8_ours.mp4`, `assets/7_src.mp4`, and `assets/7_ours.mp4`.
- Draggable vertical comparison slider that reveals the edited video over the source video.
- Left and right arrow buttons for switching between demo video pairs.
- Modal login/register card with the existing username and password fields.
- Mascot eye-follow interaction on the login modal.

After login, the original FlowStudio editor remains unchanged. The studio supports:

- MP4/MOV/WebM video selection and local preview.
- Optional source prompt and required target prompt.
- Target word input for the video editing runner.
- PNG/JPG mask upload.
- Rectangle mask drawing on the first video frame.
- Multipart submission to `POST /api/tasks/edit`.
- Task polling, result video preview, and result download.
- Logout and delete-account actions.

## Java Backend

The backend lives in `backend/` and uses Spring Boot 3.3.5 + Java 17.

Run in mock mode for classroom rehearsal:

```bash
cd backend
mvn spring-boot:run
```

Mock mode is enabled by default:

```properties
flowstudio.mock-runner=true
```

`MockTaskRunner` copies the uploaded input video to `dataset/<username>/tasks/<taskId>/result.mp4`, so the full frontend -> Java -> result display flow works without FlowAnchor or AutoDL.

Open:

```text
http://localhost:8080
```

The backend serves the frontend through `src/main/resources/static/` and forwards `/` to `index.html`.

## Authentication and User Storage

The current project includes login, registration, current-user lookup, logout, and account deletion.

Default user storage:

```text
backend/dataset/users.json
```

Each registered user gets an isolated directory:

```text
backend/dataset/<username>/tasks/<taskId>/
```

Stored task files include the uploaded video, mask, generated `prompt.json`, and result video.

The default local user store can be replaced by MySQL through environment variables:

```bash
FLOWSTUDIO_MYSQL_ENABLED=true \
FLOWSTUDIO_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/flowstudio?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true' \
FLOWSTUDIO_MYSQL_USERNAME='root' \
FLOWSTUDIO_MYSQL_PASSWORD='YOUR_PASSWORD' \
mvn spring-boot:run
```

When MySQL is enabled and reachable, `MysqlUserStore` creates and uses the `flowstudio_users` table. If MySQL is unavailable, the configuration falls back to local `dataset/users.json`.

## AutoDL Runner

Copy `autodl/flowstudio_autodl_api.py` and `autodl/requirements.txt` next to the real AutoDL `edit.py`.

Install and start:

```bash
cd autodl
pip install -r requirements.txt
python flowstudio_autodl_api.py --host 0.0.0.0 --port 8000
```

Run the Java backend with AutoDL enabled:

```bash
cd backend
FLOWSTUDIO_MOCK_RUNNER=false \
AUTODL_BASE_URL=http://YOUR_AUTODL_HOST:8000 \
mvn spring-boot:run
```

The Java backend posts multipart data to:

```text
POST <AUTODL_BASE_URL>/edit
```

The AutoDL wrapper saves:

```text
input.mp4
mask.png
result.mp4
```

and calls `edit.py` with:

```bash
python edit.py \
  --video_path input.mp4 \
  --output_path result.mp4 \
  --src_prompt "..." \
  --tar_prompt "..." \
  --mask_path mask.png \
  --target_word "rose"
```

If `edit.py` is not present, the wrapper copies the input video to `result.mp4` as a development fallback.

## API Contract

### Authentication

- `GET /api/auth/me`
  - Returns whether the current session is authenticated.
- `POST /api/auth/register`
  - JSON body: `{ "username": "...", "password": "..." }`
  - Creates a user, starts a session, and creates the user directory.
- `POST /api/auth/login`
  - JSON body: `{ "username": "...", "password": "..." }`
  - Verifies the password hash and starts a session.
- `POST /api/auth/logout`
  - Invalidates the current session.
- `POST /api/auth/delete`
  - Deletes the current user record and that user's dataset directory.

### Editing Tasks

- `POST /api/tasks/edit`
  - Requires login.
  - Multipart fields: `projectName?`, `sourcePrompt?`, `targetPrompt`, `targetWord`, `video`, `mask`.
  - Returns task detail with `taskId` and `status`.
- `GET /api/tasks/{taskId}`
  - Requires login.
  - Returns project info, prompts, status, result URL, and error message.
- `GET /api/tasks/{taskId}/status`
  - Requires login.
  - Same response shape as task detail.
- `GET /api/tasks/{taskId}/result`
  - Requires login.
  - Returns `{ taskId, resultUrl, message }` when ready.
- `GET /api/files/{taskId}/{fileName}`
  - Requires login.
  - Streams a task file owned by the current user.
- `GET /api/health`
  - Returns backend status and AutoDL configuration state.

## Configuration

Current `application.properties`:

```properties
server.port=${PORT:8080}

flowstudio.runtime-dir=${FLOWSTUDIO_RUNTIME_DIR:runtime}
flowstudio.dataset-dir=${FLOWSTUDIO_DATASET_DIR:dataset}
flowstudio.autodl-base-url=${AUTODL_BASE_URL:}
flowstudio.mock-runner=${FLOWSTUDIO_MOCK_RUNNER:true}
flowstudio.runner-timeout-seconds=${FLOWSTUDIO_RUNNER_TIMEOUT_SECONDS:1800}

flowstudio.auth.mysql.enabled=${FLOWSTUDIO_MYSQL_ENABLED:false}
flowstudio.auth.mysql.url=${FLOWSTUDIO_MYSQL_URL:}
flowstudio.auth.mysql.username=${FLOWSTUDIO_MYSQL_USERNAME:}
flowstudio.auth.mysql.password=${FLOWSTUDIO_MYSQL_PASSWORD:}

spring.servlet.multipart.max-file-size=${FLOWSTUDIO_MAX_FILE_SIZE:1024MB}
spring.servlet.multipart.max-request-size=${FLOWSTUDIO_MAX_REQUEST_SIZE:1200MB}
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
```

## Demo Script

1. Put the source/edited login videos in both frontend asset folders:

   ```text
   assets/
   backend/src/main/resources/static/assets/
   ```

2. Start the backend in mock mode:

   ```bash
   cd backend
   mvn spring-boot:run
   ```

3. Open:

   ```text
   http://localhost:8080
   ```

4. Drag the login comparison slider and switch between the demo video pairs.
5. Click `Log in`.
6. Register a test account or log in with an existing account.
7. Upload a short MP4/MOV/WebM video.
8. Enter a target prompt and target word.
9. Draw a rectangle mask on the first frame or upload a mask image.
10. Click `Run Edit`.
11. Watch the status move through `PENDING/RUNNING/SUCCESS`.
12. Play or download the result video.
