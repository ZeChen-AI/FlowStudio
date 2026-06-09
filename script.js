const API_BASE =
  window.FLOWSTUDIO_API_BASE ||
  localStorage.getItem("FLOWSTUDIO_API_BASE") ||
  "";
const POLL_INTERVAL_MS = 1800;

const AUTH_DEMO_VIDEOS = [
  { key: "9", source: "assets/9_src.mp4", ours: "assets/9_ours.mp4" },
  { key: "8", source: "assets/8_src.mp4", ours: "assets/8_ours.mp4" },
  { key: "7", source: "assets/7_src.mp4", ours: "assets/7_ours.mp4" },
];

const authScreen = document.querySelector("#auth-screen");
const authVideoStage = document.querySelector("#auth-video-stage");
const authSourceVideo = document.querySelector("#auth-source-video");
const authOursVideo = document.querySelector("#auth-ours-video");
const authRevealHandle = document.querySelector("#auth-reveal-handle");
const authPrevVideo = document.querySelector("#auth-prev-video");
const authNextVideo = document.querySelector("#auth-next-video");
const authVideoIndex = document.querySelector("#auth-video-index");
const authOpenButton = document.querySelector("#auth-open-button");
const authModalBackdrop = document.querySelector("#auth-modal-backdrop");
const authModalClose = document.querySelector("#auth-modal-close");
const authForm = document.querySelector("#auth-form");
const authTitle = document.querySelector("#auth-title");
const authSubtitle = document.querySelector("#auth-subtitle");
const authSubmit = document.querySelector("#auth-submit");
const authToggleText = document.querySelector("#auth-toggle-text");
const authToggle = document.querySelector("#auth-toggle");
const authMessage = document.querySelector("#auth-message");
const authUsername = document.querySelector("#auth-username");
const authPassword = document.querySelector("#auth-password");
const logoutButton = document.querySelector("#logout-button");
const deleteAccountButton = document.querySelector("#delete-account-button");
const currentUserLabel = document.querySelector("#current-user");
const sessionActions = document.querySelector("#flow-session-actions");

let authMode = "login";
let loggedInUsername = "";
let authVideoIndexValue = 0;
let authRevealPercent = 100;
let authDraggingReveal = false;
let authSyncTimer = 0;

function setAuthMode(nextMode) {
  authMode = nextMode;
  const isLogin = authMode === "login";
  if (authTitle) authTitle.textContent = isLogin ? "Welcome back!" : "Create account";
  if (authSubtitle) authSubtitle.textContent = isLogin ? "Please enter your details" : "Start a private FlowStudio workspace";
  if (authSubmit) authSubmit.textContent = isLogin ? "Log in" : "Sign up";
  if (authToggleText) authToggleText.textContent = isLogin ? "Don't have an account?" : "Already have an account?";
  if (authToggle) authToggle.textContent = isLogin ? "Sign Up" : "Log in";
  if (authPassword) authPassword.autocomplete = isLogin ? "current-password" : "new-password";
  setAuthMessage("");
}

function setAuthMessage(message, tone = "neutral") {
  if (!authMessage) return;
  authMessage.textContent = message;
  authMessage.dataset.tone = tone;
}

function openAuthModal(message = "", tone = "neutral") {
  if (!authScreen || !authModalBackdrop) return;
  authScreen.classList.add("is-auth-modal-open");
  authModalBackdrop.hidden = false;
  authModalBackdrop.setAttribute("aria-hidden", "false");
  if (message) setAuthMessage(message, tone);
  window.setTimeout(() => authUsername?.focus({ preventScroll: true }), 80);
}

function closeAuthModal() {
  if (!authScreen || !authModalBackdrop) return;
  authScreen.classList.remove("is-auth-modal-open");
  authModalBackdrop.hidden = true;
  authModalBackdrop.setAttribute("aria-hidden", "true");
}

function showAuth(message = "") {
  loggedInUsername = "";
  document.body.classList.add("auth-locked");
  if (authScreen) authScreen.hidden = false;
  if (sessionActions) sessionActions.hidden = true;
  if (currentUserLabel) currentUserLabel.textContent = "";
  setAuthReveal(100);
  playAuthVideos();
  if (message) {
    openAuthModal(message, message.toLowerCase().includes("login") ? "error" : "success");
  } else {
    closeAuthModal();
    setAuthMessage("");
  }
}

function showApp(username) {
  loggedInUsername = username;
  document.body.classList.remove("auth-locked");
  if (authScreen) authScreen.hidden = true;
  if (sessionActions) sessionActions.hidden = false;
  if (currentUserLabel) currentUserLabel.textContent = username ? `@${username}` : "";
  closeAuthModal();
  pauseAuthVideos();
}

async function readJson(response) {
  const text = await response.text();
  if (!text) return {};
  try { return JSON.parse(text); } catch { return { message: text }; }
}

async function authJson(path, body = undefined) {
  const options = { method: body ? "POST" : "GET", credentials: "include", headers: body ? { "Content-Type": "application/json" } : {} };
  if (body) options.body = JSON.stringify(body);
  const response = await fetch(`${API_BASE}${path}`, options);
  const data = await readJson(response);
  if (!response.ok) throw new Error(data.errorMessage || data.message || "Request failed.");
  return data;
}

async function checkCurrentUser() {
  try {
    const data = await authJson("/api/auth/me");
    if (data.authenticated) showApp(data.username); else showAuth();
  } catch { showAuth(); }
}

function handleUnauthorized(response) {
  if (response.status === 401) {
    showAuth("Please login first.");
    throw new Error("Please login first.");
  }
}

function setAuthReveal(percent) {
  authRevealPercent = Math.max(0, Math.min(100, Number(percent) || 0));
  authScreen?.style.setProperty("--auth-reveal-x", `${authRevealPercent}%`);
  authRevealHandle?.setAttribute("aria-valuenow", String(Math.round(authRevealPercent)));
  authScreen?.classList.toggle("has-auth-ours-label", authRevealPercent < 72);
}

function revealPercentFromEvent(event) {
  if (!authVideoStage) return authRevealPercent;
  const bounds = authVideoStage.getBoundingClientRect();
  return ((event.clientX - bounds.left) / bounds.width) * 100;
}

function updateRevealFromEvent(event) { setAuthReveal(revealPercentFromEvent(event)); }

function playAuthVideos() {
  [authSourceVideo, authOursVideo].forEach((video) => {
    if (!video) return;
    video.muted = true;
    const playPromise = video.play();
    if (playPromise?.catch) playPromise.catch(() => {});
  });
}

function pauseAuthVideos() { [authSourceVideo, authOursVideo].forEach((video) => video?.pause()); }

function syncAuthVideos(force = false) {
  if (!authSourceVideo || !authOursVideo) return;
  if (!Number.isFinite(authSourceVideo.currentTime)) return;
  const drift = Math.abs(authSourceVideo.currentTime - authOursVideo.currentTime);
  if (force || drift > 0.12) {
    try { authOursVideo.currentTime = authSourceVideo.currentTime; } catch {}
  }
}

function setAuthVideo(index) {
  if (!AUTH_DEMO_VIDEOS.length || !authSourceVideo || !authOursVideo) return;
  authVideoIndexValue = (index + AUTH_DEMO_VIDEOS.length) % AUTH_DEMO_VIDEOS.length;
  const item = AUTH_DEMO_VIDEOS[authVideoIndexValue];
  authSourceVideo.src = item.source;
  authOursVideo.src = item.ours;
  authSourceVideo.load();
  authOursVideo.load();
  if (authVideoIndex) authVideoIndex.textContent = `${String(authVideoIndexValue + 1).padStart(2, "0")} / ${String(AUTH_DEMO_VIDEOS.length).padStart(2, "0")}`;
  setAuthReveal(100);
  playAuthVideos();
}

function initializeAuthPreview() {
  setAuthReveal(100);
  setAuthVideo(0);
  authOpenButton?.addEventListener("click", () => openAuthModal());
  authModalClose?.addEventListener("click", closeAuthModal);
  authModalBackdrop?.addEventListener("pointerdown", (event) => { if (event.target === authModalBackdrop) closeAuthModal(); });
  authPrevVideo?.addEventListener("click", () => setAuthVideo(authVideoIndexValue - 1));
  authNextVideo?.addEventListener("click", () => setAuthVideo(authVideoIndexValue + 1));
  authVideoStage?.addEventListener("pointerdown", (event) => {
    if (event.target.closest("button, a, input, textarea, form, .flow-auth-card")) return;
    authDraggingReveal = true;
    authVideoStage.setPointerCapture?.(event.pointerId);
    updateRevealFromEvent(event);
  });
  authRevealHandle?.addEventListener("pointerdown", (event) => {
    authDraggingReveal = true;
    authRevealHandle.setPointerCapture?.(event.pointerId);
    updateRevealFromEvent(event);
    event.preventDefault();
  });
  window.addEventListener("pointermove", (event) => { if (authDraggingReveal) updateRevealFromEvent(event); });
  window.addEventListener("pointerup", () => { authDraggingReveal = false; });
  authRevealHandle?.addEventListener("keydown", (event) => {
    if (event.key === "ArrowLeft") { setAuthReveal(authRevealPercent - 4); event.preventDefault(); }
    if (event.key === "ArrowRight") { setAuthReveal(authRevealPercent + 4); event.preventDefault(); }
    if (event.key === "Home") { setAuthReveal(0); event.preventDefault(); }
    if (event.key === "End") { setAuthReveal(100); event.preventDefault(); }
  });
  authSourceVideo?.addEventListener("play", () => authOursVideo?.play()?.catch?.(() => {}));
  authSourceVideo?.addEventListener("pause", () => authOursVideo?.pause());
  authSourceVideo?.addEventListener("seeked", () => syncAuthVideos(true));
  authOursVideo?.addEventListener("loadedmetadata", () => syncAuthVideos(true));
  window.clearInterval(authSyncTimer);
  authSyncTimer = window.setInterval(() => syncAuthVideos(false), 900);
  window.addEventListener("keydown", (event) => { if (event.key === "Escape" && authScreen?.classList.contains("is-auth-modal-open")) closeAuthModal(); });
}

authToggle?.addEventListener("click", () => { setAuthMode(authMode === "login" ? "register" : "login"); });

authForm?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const username = authUsername.value.trim();
  const password = authPassword.value;
  if (!username || !password) { setAuthMessage("Please enter username and password.", "error"); return; }
  authSubmit.disabled = true;
  authSubmit.textContent = authMode === "login" ? "Logging in..." : "Creating...";
  try {
    const endpoint = authMode === "login" ? "/api/auth/login" : "/api/auth/register";
    const data = await authJson(endpoint, { username, password });
    authForm.reset();
    showApp(data.username);
  } catch (error) {
    setAuthMessage(error.message || "用户名或者密码错误", "error");
    openAuthModal();
  } finally {
    authSubmit.disabled = false;
    authSubmit.textContent = authMode === "login" ? "Log in" : "Sign up";
  }
});

logoutButton?.addEventListener("click", async () => {
  try { await authJson("/api/auth/logout", {}); } finally { clearPolling(); showAuth("Logged out."); }
});

deleteAccountButton?.addEventListener("click", async () => {
  const ok = window.confirm(`Delete account "${loggedInUsername}" and all files under dataset/${loggedInUsername}?`);
  if (!ok) return;
  try { await authJson("/api/auth/delete", {}); clearPolling(); showAuth("Account deleted."); } catch (error) { setMessage(error.message, "error"); }
});

authScreen?.addEventListener("pointermove", (event) => {
  const bounds = authScreen.getBoundingClientRect();
  const normalizedX = Math.max(-1, Math.min(1, ((event.clientX - bounds.left) / bounds.width - 0.5) * 2));
  const normalizedY = Math.max(-1, Math.min(1, ((event.clientY - bounds.top) / bounds.height - 0.5) * 2));
  authScreen.style.setProperty("--eye-offset-x", `${normalizedX * 3.5}px`);
  authScreen.style.setProperty("--eye-offset-y", `${normalizedY * 2.5}px`);
  authScreen.style.setProperty("--eye-shift-x", `${normalizedX * 5}px`);
  authScreen.style.setProperty("--eye-shift-y", `${normalizedY * 3}px`);
  authScreen.style.setProperty("--body-skew", `${normalizedX * -8}deg`);
});

initializeAuthPreview();

const form = document.querySelector("#task-form");
const projectNameInput = document.querySelector("#project-name");
const videoInput = document.querySelector("#video-input");
const maskInput = document.querySelector("#mask-input");
const sourcePromptInput = document.querySelector("#source-prompt");
const targetPromptInput = document.querySelector("#target-prompt");
const targetWordInput = document.querySelector("#target-word");
const submitButton = document.querySelector("#submit-task");
const clearMaskButton = document.querySelector("#clear-mask");
const formMessage = document.querySelector("#form-message");
const videoMeta = document.querySelector("#video-meta");
const videoPreview = document.querySelector("#video-preview");
const frameCanvas = document.querySelector("#frame-canvas");
const placeholder = document.querySelector("#canvas-placeholder");
const maskPreview = document.querySelector("#mask-preview");
const resultVideo = document.querySelector("#result-video");
const resultPlaceholder = document.querySelector("#result-placeholder");
const downloadLink = document.querySelector("#download-link");

const taskStatus = document.querySelector("#task-status");
const taskIdText = document.querySelector("#task-id");
const taskProject = document.querySelector("#task-project");
const taskPrompt = document.querySelector("#task-prompt");
const taskMessage = document.querySelector("#task-message");

const ctx = frameCanvas.getContext("2d");
let videoObjectUrl = "";
let maskObjectUrl = "";
let currentMaskBlob = null;
let currentMaskSource = "";
let baseFrame = null;
let dragStart = null;
let activeTaskId = "";
let pollTimer = 0;
let frameCaptureRequested = false;

function setMessage(message, tone = "neutral") {
  formMessage.textContent = message;
  formMessage.dataset.tone = tone;
}

function setStatus(status, message = "") {
  const labels = {
    PENDING: "Queued",
    RUNNING: "Rendering",
    SUCCESS: "Completed",
    FAILED: "Needs attention",
    READY: "Ready",
  };
  const displayStatus = labels[status] || status;
  taskStatus.innerHTML = `<span class="status-dot status-${status.toLowerCase()}"></span>${displayStatus}`;
  taskMessage.textContent = message || status;
}

function formatBytes(bytes) {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function resetResult() {
  resultVideo.removeAttribute("src");
  resultVideo.load();
  resultVideo.hidden = true;
  resultPlaceholder.hidden = false;
  downloadLink.hidden = true;
  downloadLink.removeAttribute("href");
}

function clearPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer);
    pollTimer = 0;
  }
}

function drawBaseFrame() {
  if (!baseFrame) return;
  frameCanvas.width = baseFrame.width;
  frameCanvas.height = baseFrame.height;
  frameCanvas.style.aspectRatio = `${baseFrame.width} / ${baseFrame.height}`;
  ctx.drawImage(baseFrame, 0, 0);
}

function drawSelection(rect) {
  drawBaseFrame();
  if (!rect) return;
  ctx.fillStyle = "rgba(131, 196, 156, 0.28)";
  ctx.strokeStyle = "rgba(244, 181, 91, 0.96)";
  ctx.lineWidth = Math.max(2, frameCanvas.width * 0.004);
  ctx.fillRect(rect.x, rect.y, rect.w, rect.h);
  ctx.strokeRect(rect.x, rect.y, rect.w, rect.h);
}

function getCanvasPoint(event) {
  const bounds = frameCanvas.getBoundingClientRect();
  const scaleX = frameCanvas.width / bounds.width;
  const scaleY = frameCanvas.height / bounds.height;
  return {
    x: (event.clientX - bounds.left) * scaleX,
    y: (event.clientY - bounds.top) * scaleY,
  };
}

function normalizeRect(start, end) {
  return {
    x: Math.max(0, Math.min(start.x, end.x)),
    y: Math.max(0, Math.min(start.y, end.y)),
    w: Math.abs(end.x - start.x),
    h: Math.abs(end.y - start.y),
  };
}

function updateMaskPreview(blob, source) {
  if (maskObjectUrl) URL.revokeObjectURL(maskObjectUrl);
  maskObjectUrl = URL.createObjectURL(blob);
  maskPreview.src = maskObjectUrl;
  maskPreview.hidden = false;
  currentMaskBlob = blob;
  currentMaskSource = source;
  setMessage(source === "drawn" ? "Selection mask created." : "Selection mask loaded.", "success");
}

function generateMask(rect) {
  if (rect.w < 8 || rect.h < 8) {
    setMessage("Draw a larger selection region.", "error");
    return;
  }

  const maskCanvas = document.createElement("canvas");
  maskCanvas.width = frameCanvas.width;
  maskCanvas.height = frameCanvas.height;
  const maskCtx = maskCanvas.getContext("2d");
  maskCtx.fillStyle = "#000";
  maskCtx.fillRect(0, 0, maskCanvas.width, maskCanvas.height);
  maskCtx.fillStyle = "#fff";
  maskCtx.fillRect(rect.x, rect.y, rect.w, rect.h);
  maskCanvas.toBlob((blob) => {
    if (blob) updateMaskPreview(blob, "drawn");
  }, "image/png");
}

function captureFirstFrame() {
  const width = videoPreview.videoWidth || 960;
  const height = videoPreview.videoHeight || 540;
  frameCanvas.width = width;
  frameCanvas.height = height;
  frameCanvas.style.aspectRatio = `${width} / ${height}`;
  ctx.drawImage(videoPreview, 0, 0, width, height);

  baseFrame = new Image();
  baseFrame.onload = () => {
    placeholder.hidden = true;
    frameCanvas.hidden = false;
    drawBaseFrame();
  };
  baseFrame.src = frameCanvas.toDataURL("image/png");
}

videoInput?.addEventListener("change", () => {
  const file = videoInput.files?.[0];
  currentMaskBlob = null;
  currentMaskSource = "";
  maskInput.value = "";
  maskPreview.removeAttribute("src");
  maskPreview.hidden = true;
  baseFrame = null;
  frameCaptureRequested = false;
  frameCanvas.style.removeProperty("aspect-ratio");
  ctx.clearRect(0, 0, frameCanvas.width, frameCanvas.height);
  frameCanvas.hidden = true;
  placeholder.hidden = false;

  if (videoObjectUrl) URL.revokeObjectURL(videoObjectUrl);
  resetResult();

  if (!file) {
    videoMeta.textContent = "No clip selected";
    videoPreview.removeAttribute("src");
    videoPreview.load();
    return;
  }

  videoObjectUrl = URL.createObjectURL(file);
  videoPreview.src = videoObjectUrl;
  videoPreview.pause();
  videoMeta.textContent = `${file.name} · ${formatBytes(file.size)} · ${file.type || "video"}`;
  setMessage("Clip loaded. Draw a region on the first frame or upload a mask.", "success");
});

videoPreview?.addEventListener("loadeddata", () => {
  if (frameCaptureRequested) return;
  frameCaptureRequested = true;
  const targetTime = Math.min(0.1, Math.max(0, (videoPreview.duration || 0) - 0.01));
  if (Number.isFinite(targetTime) && targetTime > 0) {
    videoPreview.currentTime = targetTime;
  } else if (videoPreview.readyState >= 2) {
    captureFirstFrame();
  }
});

videoPreview?.addEventListener("seeked", () => {
  captureFirstFrame();
});

maskInput?.addEventListener("change", () => {
  const file = maskInput.files?.[0];
  if (!file) return;
  if (!["image/png", "image/jpeg"].includes(file.type)) {
    setMessage("Selection masks must be PNG or JPG.", "error");
    maskInput.value = "";
    return;
  }
  updateMaskPreview(file, "uploaded");
});

clearMaskButton?.addEventListener("click", () => {
  currentMaskBlob = null;
  currentMaskSource = "";
  maskInput.value = "";
  maskPreview.removeAttribute("src");
  maskPreview.hidden = true;
  drawBaseFrame();
  setMessage("Selection cleared. Draw a new region or upload a mask.");
});

frameCanvas?.addEventListener("pointerdown", (event) => {
  if (!baseFrame) return;
  frameCanvas.setPointerCapture(event.pointerId);
  dragStart = getCanvasPoint(event);
});

frameCanvas?.addEventListener("pointermove", (event) => {
  if (!dragStart) return;
  const rect = normalizeRect(dragStart, getCanvasPoint(event));
  drawSelection(rect);
});

frameCanvas?.addEventListener("pointerup", (event) => {
  if (!dragStart) return;
  const rect = normalizeRect(dragStart, getCanvasPoint(event));
  dragStart = null;
  drawSelection(rect);
  generateMask(rect);
});

async function fetchTask(taskId) {
  const response = await fetch(`${API_BASE}/api/tasks/${taskId}`, { credentials: "include" });
  handleUnauthorized(response);
  if (!response.ok) throw new Error("Unable to fetch task status.");
  return response.json();
}

function renderTask(task) {
  activeTaskId = task.taskId || activeTaskId;
  taskIdText.textContent = activeTaskId || "-";
  taskProject.textContent = task.projectName || projectNameInput.value || "-";
  taskPrompt.textContent = task.targetPrompt || targetPromptInput.value || "-";
  setStatus(task.status || "READY", task.errorMessage || task.message || "Render updated.");

  if (task.status === "SUCCESS" && task.resultUrl) {
    resultVideo.src = task.resultUrl;
    resultVideo.hidden = false;
    resultPlaceholder.hidden = true;
    downloadLink.href = task.resultUrl;
    downloadLink.hidden = false;
    clearPolling();
    submitButton.disabled = false;
    submitButton.textContent = "Run Edit";
    setMessage("Edit completed. Preview is ready.", "success");
  }

  if (task.status === "FAILED") {
    clearPolling();
    submitButton.disabled = false;
    submitButton.textContent = "Run Edit";
    setMessage(task.errorMessage || "The edit needs attention.", "error");
  }
}

function startPolling(taskId) {
  clearPolling();
  pollTimer = window.setInterval(async () => {
    try {
      const task = await fetchTask(taskId);
      renderTask(task);
    } catch (error) {
      setMessage(error.message, "error");
    }
  }, POLL_INTERVAL_MS);
}

form?.addEventListener("submit", async (event) => {
  event.preventDefault();

  if (!loggedInUsername) {
    showAuth("Please login first.");
    return;
  }

  const video = videoInput.files?.[0];
  const targetPrompt = targetPromptInput.value.trim();
  const targetWord = targetWordInput.value.trim();

  if (!video) {
    setMessage("Select a source clip before rendering.", "error");
    return;
  }
  if (!targetPrompt) {
    setMessage("Edit direction is required.", "error");
    return;
  }
  if (!targetWord) {
    setMessage("Focus word is required.", "error");
    return;
  }
  if (!currentMaskBlob) {
    setMessage("Draw a selection region or upload a mask.", "error");
    return;
  }

  const formData = new FormData();
  formData.append("projectName", projectNameInput.value.trim());
  formData.append("sourcePrompt", sourcePromptInput.value.trim());
  formData.append("targetPrompt", targetPrompt);
  formData.append("targetWord", targetWord);
  formData.append("video", video, video.name);
  formData.append("mask", currentMaskBlob, currentMaskSource === "uploaded" ? "mask-upload.png" : "mask-bbox.png");

  submitButton.disabled = true;
  submitButton.textContent = "Preparing...";
  resetResult();
  setStatus("PENDING", "Preparing assets for rendering.");
  setMessage("Sending the edit to FlowStudio...");

  try {
    const response = await fetch(`${API_BASE}/api/tasks/edit`, {
      method: "POST",
      credentials: "include",
      body: formData,
    });
    handleUnauthorized(response);
    const data = await response.json();
    if (!response.ok) throw new Error(data.errorMessage || data.message || "Render request failed.");

    activeTaskId = data.taskId;
    renderTask(data);
    submitButton.textContent = "Rendering...";
    setMessage("Edit queued. Tracking render progress.", "success");
    startPolling(data.taskId);
  } catch (error) {
    submitButton.disabled = false;
    submitButton.textContent = "Run Edit";
    setStatus("FAILED", error.message);
    setMessage(error.message, "error");
  }
});

resetResult();
setAuthMode("login");
checkCurrentUser();
