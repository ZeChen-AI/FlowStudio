const API_BASE =
  window.FLOWSTUDIO_API_BASE ||
  localStorage.getItem("FLOWSTUDIO_API_BASE") ||
  "";

const leaveLink = document.querySelector("#manage-leave");
const adminBadge = document.querySelector("#admin-badge");
const loginPanel = document.querySelector("#admin-login-panel");
const usersPanel = document.querySelector("#admin-users-panel");
const loginForm = document.querySelector("#admin-login-form");
const usernameInput = document.querySelector("#admin-username");
const passwordInput = document.querySelector("#admin-password");
const submitButton = document.querySelector("#admin-submit");
const loginMessage = document.querySelector("#admin-login-message");
const usersMessage = document.querySelector("#admin-users-message");
const usersList = document.querySelector("#admin-users-list");
const previousButton = document.querySelector("#admin-prev");
const nextButton = document.querySelector("#admin-next");
const pageLabel = document.querySelector("#admin-page");

let currentPage = 0;
let totalPages = 1;
let adminAuthenticated = false;

async function readJson(response) {
  const text = await response.text();
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

async function requestJson(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    cache: "no-store",
    ...options,
  });
  const data = await readJson(response);
  if (!response.ok) {
    const error = new Error(
      data.errorMessage || data.message || "Request failed.",
    );
    error.status = response.status;
    throw error;
  }
  return data;
}

function setMessage(element, text = "", tone = "neutral") {
  element.textContent = text;
  element.dataset.tone = tone;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function formatDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function showLogin(message = "") {
  adminAuthenticated = false;
  adminBadge.textContent = "";
  usersPanel.hidden = true;
  loginPanel.hidden = false;
  usersList.innerHTML = "";
  loginForm.reset();
  setMessage(loginMessage, message, message ? "error" : "neutral");
  window.setTimeout(() => usernameInput.focus({ preventScroll: true }), 60);
}

function renderUsers(data) {
  currentPage = Number(data.page || 0);
  totalPages = Math.max(1, Number(data.totalPages || 0));
  pageLabel.textContent = `Page ${currentPage + 1} / ${totalPages}`;
  previousButton.disabled = currentPage <= 0;
  nextButton.disabled = currentPage + 1 >= totalPages;

  const items = Array.isArray(data.items) ? data.items : [];
  if (!items.length) {
    usersList.innerHTML = `
      <div class="portal-empty">No registered users were found.</div>`;
    return;
  }

  usersList.innerHTML = items
    .map(
      (user) => `
        <article class="portal-card admin-user-card">
          <div class="admin-user-fields">
            <div class="admin-user-field">
              <span>Username</span>
              <strong>${escapeHtml(user.username)}</strong>
            </div>
            <div class="admin-user-field">
              <span>User Password</span>
              <strong>${escapeHtml(
                user.passwordDisplay || "•••••••• (protected)",
              )}</strong>
            </div>
            <div class="admin-user-field">
              <span>Created At</span>
              <strong>${escapeHtml(formatDate(user.createdAt))}</strong>
            </div>
            <div class="admin-user-field">
              <span>Interactions</span>
              <strong>${Number(user.interactionCount || 0)}</strong>
            </div>
          </div>
          <button
            class="portal-danger"
            type="button"
            data-delete-user="${escapeHtml(user.username)}"
          >Delete User</button>
        </article>`,
    )
    .join("");
}

async function loadUsers(page = 0) {
  setMessage(usersMessage, "Loading users...");
  try {
    const data = await requestJson(`/api/admin/users?page=${Math.max(0, page)}`);
    adminAuthenticated = true;
    loginPanel.hidden = true;
    usersPanel.hidden = false;
    renderUsers(data);

    const total = Number(data.totalItems || 0);
    setMessage(
      usersMessage,
      `${total} registered user${total === 1 ? "" : "s"}.`,
      "success",
    );
  } catch (error) {
    if (error.status === 401) {
      showLogin("Administrator login is required.");
      return;
    }
    setMessage(usersMessage, error.message, "error");
  }
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const username = usernameInput.value.trim();
  const password = passwordInput.value;
  if (!username || !password) {
    setMessage(
      loginMessage,
      "Enter administrator username and password.",
      "error",
    );
    return;
  }

  submitButton.disabled = true;
  submitButton.textContent = "Checking...";

  try {
    const session = await requestJson("/api/admin/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });

    adminAuthenticated = true;
    adminBadge.textContent = `Admin @${session.username}`;
    loginForm.reset();
    await loadUsers(0);
  } catch (error) {
    showLogin(error.message || "Administrator login failed.");
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "Enter Manage";
  }
});

usersList.addEventListener("click", async (event) => {
  const button = event.target.closest("[data-delete-user]");
  if (!button) return;

  const username = button.dataset.deleteUser;
  const confirmed = window.confirm(
    `Delete user "${username}" and all local and AutoDL interaction data?`,
  );
  if (!confirmed) return;

  button.disabled = true;
  setMessage(usersMessage, `Deleting @${username}...`);

  try {
    await requestJson(
      `/api/admin/users/${encodeURIComponent(username)}`,
      { method: "DELETE" },
    );
    await loadUsers(currentPage);
    setMessage(usersMessage, `User @${username} deleted.`, "success");
  } catch (error) {
    button.disabled = false;
    setMessage(usersMessage, error.message, "error");
  }
});

previousButton.addEventListener("click", () => loadUsers(currentPage - 1));
nextButton.addEventListener("click", () => loadUsers(currentPage + 1));

leaveLink.addEventListener("click", async (event) => {
  if (!adminAuthenticated) return;

  event.preventDefault();
  try {
    await requestJson("/api/admin/logout", { method: "POST" });
  } catch {
    // Navigation should still continue even if the session already expired.
  }
  window.location.href = "index.html";
});

async function initialize() {
  try {
    const session = await requestJson("/api/admin/me");
    if (session.authenticated) {
      adminAuthenticated = true;
      adminBadge.textContent = `Admin @${session.username}`;
      await loadUsers(0);
      return;
    }
  } catch {
    // The login panel is the normal fallback.
  }

  showLogin();
}

initialize();
