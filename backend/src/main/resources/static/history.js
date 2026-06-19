const API_BASE =
  window.FLOWSTUDIO_API_BASE ||
  localStorage.getItem("FLOWSTUDIO_API_BASE") ||
  "";

const userLabel = document.querySelector("#history-user");
const message = document.querySelector("#history-message");
const list = document.querySelector("#history-list");
const previousButton = document.querySelector("#history-prev");
const nextButton = document.querySelector("#history-next");
const pageLabel = document.querySelector("#history-page");

let currentPage = 0;
let totalPages = 1;

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

function setMessage(text = "", tone = "neutral") {
  message.textContent = text;
  message.dataset.tone = tone;
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

function mediaMarkup(title, fileName, url, type) {
  const displayName = escapeHtml(fileName || title);
  if (!url) {
    return `
      <section class="history-media">
        <h3>${displayName}</h3>
        <div class="media-unavailable">Not available</div>
      </section>`;
  }

  const safeUrl = escapeHtml(url);
  const media =
    type === "image"
      ? `<img src="${safeUrl}" alt="${escapeHtml(title)}" loading="lazy" />`
      : `<video src="${safeUrl}" controls playsinline preload="metadata"></video>`;

  return `
    <section class="history-media">
      <h3>${displayName}</h3>
      ${media}
    </section>`;
}

function downloadMarkup(label, fileName, url) {
  if (!url) {
    return `<span class="portal-primary is-disabled">${escapeHtml(label)} unavailable</span>`;
  }

  return `
    <a
      class="portal-primary"
      href="${escapeHtml(url)}"
      download="${escapeHtml(fileName || "")}"
    >${escapeHtml(label)}</a>`;
}

function stopRenderedVideos() {
  list.querySelectorAll("video").forEach((video) => {
    video.pause();
    video.removeAttribute("src");
    video.load();
  });
}

function renderPage(data) {
  stopRenderedVideos();

  currentPage = Number(data.page || 0);
  totalPages = Math.max(1, Number(data.totalPages || 0));
  pageLabel.textContent = `Page ${currentPage + 1} / ${totalPages}`;
  previousButton.disabled = currentPage <= 0;
  nextButton.disabled = currentPage + 1 >= totalPages;

  const items = Array.isArray(data.items) ? data.items : [];
  if (!items.length) {
    list.innerHTML = `
      <div class="portal-empty">
        No interaction history has been saved for this account.
      </div>`;
    return;
  }

  list.innerHTML = items
    .map(
      (item) => `
        <article class="portal-card history-card">
          <header class="history-card-header">
            <div>
              <h2>${escapeHtml(item.projectName || "Untitled Project")}</h2>
              <div class="history-meta">
                <span>${escapeHtml(item.taskId)}</span>
                <span>${escapeHtml(formatDate(item.createdAt))}</span>
              </div>
            </div>
            <span
              class="history-status"
              data-status="${escapeHtml(item.status)}"
            >${escapeHtml(item.status)}</span>
          </header>

          <div class="history-media-grid">
            ${mediaMarkup(
              "Selection mask",
              item.maskFileName || "mask.png",
              item.maskUrl,
              "image",
            )}
            ${mediaMarkup(
              "Input video",
              item.inputFileName || "input.mp4",
              item.inputUrl,
              "video",
            )}
            ${mediaMarkup(
              "Result video",
              item.resultFileName || "result.mp4",
              item.resultUrl,
              "video",
            )}
          </div>

          <footer class="history-card-footer">
            <div class="history-downloads">
              ${downloadMarkup("Download Mask", item.maskFileName, item.maskUrl)}
              ${downloadMarkup("Download Input", item.inputFileName, item.inputUrl)}
              ${downloadMarkup("Download Result", item.resultFileName, item.resultUrl)}
            </div>
            <button
              class="portal-danger"
              type="button"
              data-delete-task="${escapeHtml(item.taskId)}"
            >Delete Record</button>
          </footer>
        </article>`,
    )
    .join("");
}

async function loadHistory(page = 0) {
  setMessage("Loading history...");
  try {
    const data = await requestJson(`/api/history?page=${Math.max(0, page)}`);
    renderPage(data);
    const total = Number(data.totalItems || 0);
    setMessage(
      `${total} saved interaction${total === 1 ? "" : "s"}.`,
      "success",
    );
  } catch (error) {
    if (error.status === 401) {
      window.location.replace("index.html");
      return;
    }
    setMessage(error.message, "error");
  }
}

list.addEventListener("click", async (event) => {
  const button = event.target.closest("[data-delete-task]");
  if (!button) return;

  const taskId = button.dataset.deleteTask;
  const confirmed = window.confirm(
    `Delete interaction "${taskId}" and all of its files?`,
  );
  if (!confirmed) return;

  button.disabled = true;
  setMessage("Deleting interaction...");

  try {
    await requestJson(`/api/history/${encodeURIComponent(taskId)}`, {
      method: "DELETE",
    });
    await loadHistory(currentPage);
    setMessage("Interaction deleted.", "success");
  } catch (error) {
    button.disabled = false;
    setMessage(error.message, "error");
  }
});

previousButton.addEventListener("click", () => loadHistory(currentPage - 1));
nextButton.addEventListener("click", () => loadHistory(currentPage + 1));

window.addEventListener("beforeunload", stopRenderedVideos);

async function initialize() {
  try {
    const session = await requestJson("/api/auth/me");
    if (!session.authenticated) {
      window.location.replace("index.html");
      return;
    }

    userLabel.textContent = `@${session.username}`;
    await loadHistory(0);
  } catch {
    window.location.replace("index.html");
  }
}

initialize();
