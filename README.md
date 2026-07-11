# FlowStudio

FlowStudio 是一个面向课程展示与实验验证的 AI 视频局部编辑 Web 系统。用户可以上传视频、在首帧中绘制或上传遮罩、输入文本编辑指令，并通过本地 Mock Runner 或 AutoDL 推理服务生成编辑结果。

当前项目由原生 HTML/CSS/JavaScript 前端、Java Spring Boot 后端、用户与任务持久化模块，以及可选的 AutoDL FastAPI 服务组成。系统已实现用户数据隔离、历史记录、管理员管理、任务文件下载与删除等完整功能。

## 功能概览

### 用户功能

- 用户注册、登录、登出与账户注销。
- 登录页支持 Source/FlowStudio 视频对比和拖拽切换。
- 上传 MP4、MOV、WebM 视频。
- 上传 PNG、JPG、JPEG 遮罩。
- 在视频首帧上绘制矩形遮罩。
- 输入原始场景描述、目标编辑指令和目标关键词。
- 提交视频编辑任务并轮询任务状态。
- 预览和下载生成结果。
- 不同用户之间的数据、任务和文件相互隔离。
- 登出或切换用户时自动清空当前工作区状态。
- 注销账户时删除该用户本地及 AutoDL 端的数据。

### History 历史记录

History 使用独立页面 `history.html`，并以 `studio-hero.png` 裁切后作为背景。

- 仅展示当前登录用户的历史记录。
- 每页显示 5 条记录。
- 展示项目名称、任务状态和创建时间。
- 预览 `mask`、`input` 和 `result` 文件。
- 分别下载遮罩、输入视频和结果视频。
- 删除单条历史记录及其相关文件。
- 左上角 `Leave` 返回视频生成页面。

### Manage 管理后台

Manage 使用独立页面 `manage.html`。

- 管理员独立登录。
- 每页显示 5 个用户。
- 展示用户名、密码保护状态、创建时间和交互次数。
- 管理员可以删除指定用户及其所有任务数据。
- 用户密码采用单向哈希保存，管理界面不会展示明文密码。
- 左上角 `Leave` 返回视频生成页面并退出管理员会话。

默认课程演示管理员账号：

```text
Username: hihihihi
Password: 666666
```

公开部署前应通过环境变量修改默认管理员密码。

---

## 项目结构

```text
FlowStudio/
├── assets/                         # 根目录前端资源
│   ├── 7_ours.mp4
│   ├── 7_src.mp4
│   ├── 8_ours.mp4
│   ├── 8_src.mp4
│   ├── 9_ours.mp4
│   ├── 9_src.mp4
│   ├── edit-canvas.png
│   ├── login-reference.jpg
│   └── studio-hero.png
├── autodl/
│   ├── flowstudio_autodl_api.py   # AutoDL FastAPI 包装服务
│   └── requirements.txt
├── backend/
│   ├── dataset/                    # 用户、任务和上传文件
│   │   ├── users.json
│   │   └── <username>/
│   │       ├── user.json
│   │       └── tasks/
│   │           └── <taskId>/
│   │               ├── input.mp4
│   │               ├── mask.png
│   │               ├── prompt.json
│   │               ├── task.json
│   │               └── result.mp4
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/studio/flow/
│   │   │   │   ├── config/
│   │   │   │   ├── controller/
│   │   │   │   ├── dto/
│   │   │   │   ├── model/
│   │   │   │   ├── runner/
│   │   │   │   ├── service/
│   │   │   │   └── FlowStudioApplication.java
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── static/
│   │   │           ├── assets/
│   │   │           ├── index.html
│   │   │           ├── script.js
│   │   │           ├── styles.css
│   │   │           ├── history.html
│   │   │           ├── history.js
│   │   │           ├── manage.html
│   │   │           ├── manage.js
│   │   │           └── portal.css
│   │   └── test/
│   ├── target/                     # Maven 构建产物，请勿手动修改
│   └── pom.xml
├── index.html                      # 根目录前端开发副本
├── script.js
├── styles.css
├── history.html
├── history.js
├── manage.html
├── manage.js
├── portal.css
└── README.md
```

运行 Spring Boot 时，实际使用的是：

```text
backend/src/main/resources/static/
```

根目录中的 HTML、CSS 和 JavaScript 可用于前端调试，但修改时应确保两处文件保持同步。

`backend/target/` 为 Maven 自动生成目录，不应直接编辑。

---

## 技术架构

### 前端

- 原生 HTML5
- 原生 CSS3
- 原生 JavaScript
- Fetch API
- Canvas API
- Video API
- Session Cookie

### 后端

- Java 17
- Spring Boot 3.3.5
- Maven
- Jackson
- Java HttpClient
- 本地 JSON 或 MySQL 用户存储

### 推理服务

- Python
- FastAPI
- Uvicorn
- AutoDL
- 外部 `edit.py` 视频编辑程序

---

## 页面说明

### `index.html`

主视频编辑页面，包括登录注册、视频上传、遮罩绘制、Prompt 输入、任务提交、任务状态、结果播放与下载，以及 History、Manage 和 GitHub 导航。

### `history.html`

当前用户的历史记录页面，对应：

```text
history.js
portal.css
```

### `manage.html`

管理员登录与用户管理页面，对应：

```text
manage.js
portal.css
```

---

## 数据存储

默认数据目录：

```text
backend/dataset/
```

### 用户列表

```text
backend/dataset/users.json
```

用户密码不会以明文保存，而是通过 `PasswordHasher` 进行单向哈希。

### 用户目录

每个用户拥有独立目录：

```text
backend/dataset/<username>/
```

用户基本元数据：

```text
backend/dataset/<username>/user.json
```

### 任务目录

每次交互保存到：

```text
backend/dataset/<username>/tasks/<taskId>/
```

典型文件：

```text
input.mp4       # 用户上传的视频
mask.png        # 用户上传或绘制的遮罩
prompt.json     # Prompt 与任务输入信息
task.json       # 任务状态和持久化信息
result.mp4      # 最终生成结果
```

后端启动时会扫描 `task.json` 并恢复历史任务。后端重启前仍处于 `PENDING` 或 `RUNNING` 的任务会标记为中断失败，避免永久停留在运行状态。

---

## 用户隔离与会话管理

系统通过 Spring Session 和任务所有者校验实现用户隔离。

- 任务创建时从服务端 Session 获取用户名。
- 查询任务时同时校验用户名和任务 ID。
- 文件下载时校验任务所有权。
- History 只能读取当前用户的任务。
- 不接受前端提交的用户名作为任务所有者。
- 用户登出时，前端清空视频、遮罩、Prompt、任务 ID 和结果。
- 前端通过请求中止和会话代次校验，避免旧用户的异步响应写入新用户页面。
- 管理员删除用户后，该用户旧 Session 会自动失效。

---

## 启动后端

项目默认启用 Mock Runner，适合本地开发与课堂演示。

```bash
cd backend
mvn spring-boot:run
```

浏览器访问：

```text
http://localhost:8080
```

也可以先构建 JAR：

```bash
cd backend
mvn clean package
java -jar target/flowstudio-backend-0.0.1-SNAPSHOT.jar
```

Mock Runner 会将输入视频复制为结果视频，用于验证完整的前端、后端和文件保存流程。

---

## AutoDL 服务

安装依赖：

```bash
cd autodl
pip install -r requirements.txt
```

将真实的 `edit.py` 放在：

```text
autodl/edit.py
```

启动服务：

```bash
export FLOWSTUDIO_AUTODL_TOKEN='replace-with-a-long-random-token'

python flowstudio_autodl_api.py   --host 0.0.0.0   --port 8000
```

AutoDL 数据按用户和任务隔离：

```text
autodl/flowstudio_runtime/
└── users/
    └── <username>/
        └── tasks/
            └── <taskId>/
                ├── input.mp4
                ├── mask.png
                ├── edit.log
                └── result.mp4
```

如果 `edit.py` 不存在，AutoDL 包装服务会将输入视频复制到 `result.mp4`，用于开发测试。

Java 后端连接 AutoDL：

```bash
cd backend

export FLOWSTUDIO_MOCK_RUNNER=false
export AUTODL_BASE_URL=http://YOUR_AUTODL_HOST:8000
export FLOWSTUDIO_AUTODL_TOKEN='replace-with-a-long-random-token'

mvn spring-boot:run
```

Java 后端和 AutoDL 必须使用相同的 `FLOWSTUDIO_AUTODL_TOKEN`。

---

## 管理员配置

默认配置位于 `application.properties`：

```properties
flowstudio.admin.username=${FLOWSTUDIO_ADMIN_USERNAME:hihihihi}
flowstudio.admin.password=${FLOWSTUDIO_ADMIN_PASSWORD:666666}
```

推荐通过环境变量覆盖：

```bash
export FLOWSTUDIO_ADMIN_USERNAME=hihihihi
export FLOWSTUDIO_ADMIN_PASSWORD='YOUR_STRONG_ADMIN_PASSWORD'
```

---

## MySQL 用户存储

默认使用：

```text
backend/dataset/users.json
```

启用 MySQL：

```bash
cd backend

export FLOWSTUDIO_MYSQL_ENABLED=true
export FLOWSTUDIO_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/flowstudio?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'
export FLOWSTUDIO_MYSQL_USERNAME='root'
export FLOWSTUDIO_MYSQL_PASSWORD='YOUR_PASSWORD'

mvn spring-boot:run
```

启用后，系统会创建或使用 `flowstudio_users` 表。

---

## 主要 API

### 用户认证

```text
GET  /api/auth/me
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
POST /api/auth/delete
```

### 视频任务

```text
POST /api/tasks/edit
GET  /api/tasks/{taskId}
GET  /api/tasks/{taskId}/status
GET  /api/tasks/{taskId}/result
GET  /api/files/{taskId}/{fileName}
```

创建任务使用 `multipart/form-data`：

```text
projectName
sourcePrompt
targetPrompt
targetWord
video
mask
```

### 历史记录

```text
GET    /api/history?page=0
DELETE /api/history/{taskId}
```

History 固定每页返回 5 条记录。

### 管理员

```text
GET    /api/admin/me
POST   /api/admin/login
POST   /api/admin/logout
GET    /api/admin/users?page=0
DELETE /api/admin/users/{username}
```

Manage 固定每页返回 5 个用户。

### 健康检查

```text
GET /api/health
```

### AutoDL 内部接口

```text
POST   /edit
GET    /files/{username}/{taskId}/{fileName}
DELETE /internal/users/{username}
DELETE /internal/users/{username}/tasks/{taskId}
GET    /health
```

---

## 主要环境变量

```text
PORT
FLOWSTUDIO_RUNTIME_DIR
FLOWSTUDIO_DATASET_DIR
AUTODL_BASE_URL
FLOWSTUDIO_AUTODL_TOKEN
FLOWSTUDIO_MOCK_RUNNER
FLOWSTUDIO_RUNNER_TIMEOUT_SECONDS

FLOWSTUDIO_MYSQL_ENABLED
FLOWSTUDIO_MYSQL_URL
FLOWSTUDIO_MYSQL_USERNAME
FLOWSTUDIO_MYSQL_PASSWORD

FLOWSTUDIO_ADMIN_USERNAME
FLOWSTUDIO_ADMIN_PASSWORD

FLOWSTUDIO_MAX_FILE_SIZE
FLOWSTUDIO_MAX_REQUEST_SIZE
FLOWSTUDIO_COOKIE_SECURE
```

生产配置示例：

```bash
export FLOWSTUDIO_DATASET_DIR=/absolute/path/to/FlowStudio/backend/dataset
export FLOWSTUDIO_ADMIN_USERNAME=hihihihi
export FLOWSTUDIO_ADMIN_PASSWORD='YOUR_STRONG_ADMIN_PASSWORD'
export FLOWSTUDIO_AUTODL_TOKEN='YOUR_LONG_RANDOM_TOKEN'
export FLOWSTUDIO_COOKIE_SECURE=true
```

---

## 演示流程

1. 启动 Spring Boot 后端。
2. 打开 `http://localhost:8080`。
3. 在登录页拖动 Source/FlowStudio 视频对比条。
4. 注册新用户或登录已有用户。
5. 上传短视频。
6. 输入目标编辑 Prompt 和目标关键词。
7. 上传遮罩或在首帧上绘制选择区域。
8. 提交任务。
9. 等待状态从 `PENDING`、`RUNNING` 进入 `SUCCESS`。
10. 播放或下载结果视频。
11. 打开 History 查看、下载或删除历史记录。
12. 打开 Manage 并使用管理员账号查看用户和交互次数。

---

## 开发注意事项

- 不要直接修改 `backend/target/`。
- 修改前端后，应同步根目录和 `backend/src/main/resources/static/`。
- 修改 Java 或静态资源后，建议执行：

  ```bash
  cd backend
  mvn clean package
  ```

- 浏览器仍显示旧样式时进行强制刷新：

  ```text
  Windows/Linux: Ctrl + F5
  macOS: Command + Shift + R
  ```

- `backend/dataset/` 包含用户数据和视频文件，不建议提交到公开 Git 仓库。
- `users.json`、管理员密码、MySQL 密码和 AutoDL Token 均属于敏感配置。
- 生产环境应启用 HTTPS，并设置：

  ```bash
  export FLOWSTUDIO_COOKIE_SECURE=true
  ```

---

## 当前状态与展望

FlowStudio 当前已完成：

- 视频局部编辑交互闭环。
- 用户注册、登录和会话管理。
- 用户文件和任务隔离。
- 任务数据持久化和重启恢复。
- History 历史记录分页、下载和删除。
- Manage 用户分页、统计和删除。
- 本地 Mock Runner。
- AutoDL 推理服务连接。
- 本地 JSON 与 MySQL 用户存储支持。

后续可以继续加入任务取消、失败重试、历史搜索、批量下载、管理员统计图表、对象存储以及更完整的自动化测试。
