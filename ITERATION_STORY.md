# Iteration Story

This version keeps the original FlowStudio editing workflow and adds a redesigned login gate, session-based authentication, user storage, and per-user task isolation.

## Before

```text
Open page -> use editor directly -> task files in runtime/tasks/<taskId>
```

The original demo allowed the editor to be used immediately. Video upload, prompt input, mask upload or rectangle drawing, task creation, polling, result preview, and download were all available without login.

## Now

```text
Open page -> video comparison login gate -> login/register -> use editor -> task files in dataset/<username>/tasks/<taskId>
```

The current login gate shows a gray-black FlowStudio landing page with source/edited video comparison. It uses the demo video pairs in `assets/`:

```text
9_src.mp4  -> 9_ours.mp4
8_src.mp4  -> 8_ours.mp4
7_src.mp4  -> 7_ours.mp4
```

Users can drag the vertical comparison bar to reveal the edited video over the source video, switch among the video pairs with left/right arrows, and open the login/register modal through the top-right login button.

After login, the original FlowStudio editor remains in place. The task workflow is still:

```text
Upload video -> enter prompts -> provide mask -> submit task -> poll status -> preview/download result
```

## Authentication Flow

```text
Frontend auth form -> AuthController -> UserService -> UserStore
```

`UserStore` has two implementations:

```text
LocalUserStore  -> dataset/users.json
MysqlUserStore  -> flowstudio_users table
```

Local storage is the default. MySQL can be enabled through environment variables without changing the frontend.

## Task Flow

The runner abstraction remains unchanged:

```text
TaskController -> TaskService -> TaskRunner
```

`TaskController` requires a logged-in session for task creation, task lookup, status polling, result lookup, and task file access.

`TaskService` writes task files into the logged-in user's directory:

```text
dataset/<username>/tasks/<taskId>/
```

Each task directory can contain:

```text
input.<ext>
mask.<ext>
prompt.json
result.mp4
```

`MockTaskRunner` still copies the uploaded input video as the result.

`AutodlTaskRunner` still calls the AutoDL wrapper when mock mode is disabled.

## Current Boundary

The login redesign changes only the entry interface. The editing workspace, video upload behavior, first-frame mask drawing, task submission, polling, result preview, and download flow stay compatible with the existing Java API.
