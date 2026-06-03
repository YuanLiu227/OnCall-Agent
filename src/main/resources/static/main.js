import { marked } from "marked";
import DOMPurify from "dompurify";

const API_BASE = "/api";
const SESSION_KEY = "oncall-agent-session-id";
const SESSIONS_KEY = "oncall-agent-sessions-v1";
const MODE_KEY = "oncall-agent-chat-mode";
const UNTITLED = "新对话";
const WELCOME_TEXT = "你好，我是 OnCall Agent。可以直接问我运维排查、内部知识库或告警分析相关问题。";

marked.setOptions({
  gfm: true,
  breaks: true
});

const form = document.querySelector("#chatForm");
const input = document.querySelector("#questionInput");
const messages = document.querySelector("#messages");
const sendButton = document.querySelector("#sendButton");
const uploadButton = document.querySelector("#uploadButton");
const knowledgeFileInput = document.querySelector("#knowledgeFileInput");
const aiOpsButton = document.querySelector("#aiOpsButton");
const clearButton = document.querySelector("#clearButton");
const newChatButton = document.querySelector("#newChatButton");
const sessionList = document.querySelector("#sessionList");
const apiStatus = document.querySelector("#apiStatus");
const endpointText = document.querySelector("#endpointText");
const modeButtons = document.querySelectorAll("[data-mode]");

let sessions = loadSessions();
let sessionId = getInitialSessionId();
let chatMode = localStorage.getItem(MODE_KEY) || "standard";
let isSending = false;

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  await sendQuestion();
});

input.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    form.requestSubmit();
  }
});

input.addEventListener("input", resizeTextarea);

uploadButton.addEventListener("click", () => {
  if (!isSending) {
    knowledgeFileInput.click();
  }
});

knowledgeFileInput.addEventListener("change", async () => {
  const [file] = knowledgeFileInput.files;
  knowledgeFileInput.value = "";
  if (file) {
    await uploadKnowledgeFile(file);
  }
});

clearButton.addEventListener("click", () => {
  if (isSending) {
    return;
  }

  const session = getCurrentSession();
  session.messages = createWelcomeMessages();
  session.title = UNTITLED;
  session.inHistory = false;
  session.updatedAt = Date.now();
  saveSessions();
  renderSessionList();
  renderCurrentSession();
  input.focus();
});

newChatButton.addEventListener("click", () => {
  if (!isSending) {
    startNewDraftSession();
  }
});

aiOpsButton.addEventListener("click", async () => {
  await runAiOps();
});

modeButtons.forEach((button) => {
  button.addEventListener("click", () => {
    if (isSending) {
      return;
    }
    setChatMode(button.dataset.mode);
    input.focus();
  });
});

async function sendQuestion() {
  const question = input.value.trim();
  if (!question || isSending) {
    return;
  }

  appendMessage("user", question);
  input.value = "";
  resizeTextarea();
  setSending(true);

  try {
    if (chatMode === "stream") {
      await sendStreamQuestion(question);
    } else {
      await sendStandardQuestion(question);
    }
    setApiStatus(true);
  } catch (error) {
    appendMessage("assistant", `请求出错：${error.message}`);
    setApiStatus(false);
  } finally {
    setSending(false);
    input.focus();
  }
}

async function uploadKnowledgeFile(file) {
  const fileName = file.name || "";
  if (!fileName.toLowerCase().endsWith(".md")) {
    appendMessage("assistant", "目前只支持上传 `.md` 文件到知识库。");
    return;
  }

  setSending(true);
  const loadingMessage = appendLoadingMessage();
  updateAssistantMessage(loadingMessage, `正在上传 **${escapeMarkdown(fileName)}** 到知识库...`);

  try {
    const formData = new FormData();
    formData.append("file", file);

    const response = await fetch(`${API_BASE}/upload`, {
      method: "POST",
      body: formData
    });

    const text = await response.text();
    let payload = null;
    try {
      payload = JSON.parse(text);
    } catch (error) {
      payload = { message: text };
    }

    if (!response.ok || payload?.code >= 400) {
      throw new Error(payload?.message || `HTTP ${response.status}`);
    }

    replaceAssistantMessage(
      loadingMessage,
      `已提交 **${escapeMarkdown(fileName)}** 到知识库。\n\n文件大小：${formatFileSize(file.size)}\n\n后端会在上传后尝试切分文档、生成向量并写入 Milvus。`
    );
    setApiStatus(true);
  } catch (error) {
    replaceAssistantMessage(loadingMessage, `上传失败：${error.message}`);
    setApiStatus(false);
  } finally {
    setSending(false);
    input.focus();
  }
}

async function runAiOps() {
  if (isSending) {
    return;
  }

  appendMessage("user", "启动自动运维分析");
  const assistantMessage = appendStreamingMessage();
  let fullAnswer = "";

  setSending(true);
  endpointText.textContent = "/api/ai_ops";
  updateAssistantMessage(assistantMessage, "正在启动自动运维流程，请等待分析结果...");

  try {
    const response = await fetch(`${API_BASE}/ai_ops`, {
      method: "POST",
      headers: {
        Accept: "text/event-stream"
      }
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    if (!response.body) {
      throw new Error("浏览器没有收到可读取的自动运维流式响应");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split(/\r?\n\r?\n/);
      buffer = events.pop() || "";

      for (const eventText of events) {
        const message = parseSseMessage(eventText);
        if (!message) {
          continue;
        }

        if (message.type === "content") {
          fullAnswer += message.data || "";
          updateAssistantMessage(assistantMessage, fullAnswer || "自动运维分析中...");
        } else if (message.type === "error") {
          throw new Error(message.data || "自动运维接口返回错误");
        } else if (message.type === "done") {
          replaceAssistantMessage(assistantMessage, fullAnswer || "自动运维流程已完成，但没有返回报告内容。");
          setApiStatus(true);
          return;
        }
      }
    }

    replaceAssistantMessage(assistantMessage, fullAnswer || "自动运维流程已完成，但没有返回报告内容。");
    setApiStatus(true);
  } catch (error) {
    replaceAssistantMessage(assistantMessage, `自动运维失败：${error.message}`);
    setApiStatus(false);
  } finally {
    setSending(false);
    setChatMode(chatMode);
    input.focus();
  }
}

async function sendStandardQuestion(question) {
  const loadingMessage = appendLoadingMessage();
  const response = await fetch(`${API_BASE}/chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(buildRequestBody(question))
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = await response.json();
  const data = payload?.data;

  if (!data?.success) {
    throw new Error(data?.errorMessage || payload?.message || "后端返回失败");
  }

  replaceAssistantMessage(loadingMessage, data.answer || "没有返回内容");
}

async function sendStreamQuestion(question) {
  const assistantMessage = appendStreamingMessage();
  let fullAnswer = "";

  const response = await fetch(`${API_BASE}/chat_stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream"
    },
    body: JSON.stringify(buildRequestBody(question))
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  if (!response.body) {
    throw new Error("浏览器没有收到可读取的流式响应");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop() || "";

    for (const eventText of events) {
      const message = parseSseMessage(eventText);
      if (!message) {
        continue;
      }

      if (message.type === "content") {
        fullAnswer += message.data || "";
        updateAssistantMessage(assistantMessage, fullAnswer || "正在生成...");
      } else if (message.type === "error") {
        throw new Error(message.data || "流式接口返回错误");
      } else if (message.type === "done") {
        replaceAssistantMessage(assistantMessage, fullAnswer || "没有返回内容");
        return;
      }
    }
  }

  replaceAssistantMessage(assistantMessage, fullAnswer || "没有返回内容");
}

function parseSseMessage(eventText) {
  const dataLines = eventText
    .split(/\r?\n/)
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart());

  if (dataLines.length === 0) {
    return null;
  }

  const dataText = dataLines.join("\n");
  if (!dataText || dataText === "[DONE]") {
    return { type: "done" };
  }

  try {
    return JSON.parse(dataText);
  } catch (error) {
    return { type: "content", data: dataText };
  }
}

function buildRequestBody(question) {
  return {
    Id: sessionId,
    Question: question
  };
}

function appendMessage(role, text, options = {}) {
  const { persist = true } = options;
  const article = document.createElement("article");
  article.className = `message ${role}-message`;

  const avatar = document.createElement("div");
  avatar.className = "avatar";
  avatar.setAttribute("aria-hidden", "true");
  avatar.textContent = role === "user" ? "U" : "A";

  const bubble = document.createElement("div");
  bubble.className = "bubble";
  if (role === "assistant") {
    bubble.classList.add("markdown-body");
    bubble.innerHTML = renderMarkdown(text);
  } else {
    bubble.textContent = text;
  }

  article.append(avatar, bubble);
  messages.appendChild(article);

  if (persist) {
    persistMessage(role, text);
  }

  scrollToBottom();
  return article;
}

function appendLoadingMessage() {
  const article = createAssistantShell("is-loading");
  const bubble = article.querySelector(".bubble");
  bubble.innerHTML = `
    <span class="typing" aria-label="正在思考">
      <span></span><span></span><span></span>
    </span>
  `;
  messages.appendChild(article);
  scrollToBottom();
  return article;
}

function appendStreamingMessage() {
  const article = createAssistantShell("is-streaming");
  updateAssistantMessage(article, "正在连接流式接口...");
  messages.appendChild(article);
  scrollToBottom();
  return article;
}

function createAssistantShell(extraClass) {
  const article = document.createElement("article");
  article.className = `message assistant-message ${extraClass}`;

  const avatar = document.createElement("div");
  avatar.className = "avatar";
  avatar.setAttribute("aria-hidden", "true");
  avatar.textContent = "A";

  const bubble = document.createElement("div");
  bubble.className = "bubble markdown-body";

  article.append(avatar, bubble);
  return article;
}

function replaceAssistantMessage(article, text) {
  article.classList.remove("is-loading", "is-streaming");
  updateAssistantMessage(article, text);
  persistMessage("assistant", text);
}

function updateAssistantMessage(article, text) {
  const bubble = article.querySelector(".bubble");
  bubble.innerHTML = renderMarkdown(text);
  scrollToBottom();
}

function renderCurrentSession() {
  messages.innerHTML = "";
  getCurrentSession().messages.forEach((message) => {
    appendMessage(message.role, message.text, { persist: false });
  });
}

function renderSessionList() {
  sessionList.innerHTML = "";

  getHistorySessions().forEach((session) => {
    const item = document.createElement("div");
    item.className = "session-row";
    item.classList.toggle("is-active", session.id === sessionId);

    const button = document.createElement("button");
    button.className = "session-item";
    button.type = "button";
    button.classList.toggle("is-active", session.id === sessionId);
    button.setAttribute("aria-current", session.id === sessionId ? "true" : "false");

    const title = document.createElement("span");
    title.className = "session-title";
    title.textContent = getSessionDisplayTitle(session);
    button.title = title.textContent;

    const deleteButton = document.createElement("button");
    deleteButton.className = "session-delete";
    deleteButton.type = "button";
    deleteButton.setAttribute("aria-label", `删除会话：${title.textContent}`);
    deleteButton.textContent = "×";

    button.append(title);
    button.addEventListener("click", () => {
      if (isSending || session.id === sessionId) {
        return;
      }
      switchSession(session.id);
    });

    deleteButton.addEventListener("click", () => {
      if (!isSending) {
        deleteSession(session.id);
      }
    });

    item.append(button, deleteButton);
    sessionList.appendChild(item);
  });
}

function switchSession(nextSessionId) {
  archiveCurrentSessionIfNeeded();
  sessionId = nextSessionId;
  localStorage.setItem(SESSION_KEY, sessionId);
  saveSessions();
  renderSessionList();
  renderCurrentSession();
  input.focus();
}

function startNewDraftSession() {
  archiveCurrentSessionIfNeeded();
  removeEmptyDraftSessions();

  const nextSession = createSession();
  sessions.unshift(nextSession);
  sessionId = nextSession.id;
  localStorage.setItem(SESSION_KEY, sessionId);
  saveSessions();
  renderSessionList();
  renderCurrentSession();
  input.focus();
}

function deleteSession(targetSessionId) {
  const deletingCurrent = targetSessionId === sessionId;
  sessions = sessions.filter((session) => session.id !== targetSessionId);

  if (deletingCurrent) {
    const nextSession = getHistorySessions()[0] || createSession();
    if (!sessions.some((session) => session.id === nextSession.id)) {
      sessions.unshift(nextSession);
    }
    sessionId = nextSession.id;
    localStorage.setItem(SESSION_KEY, sessionId);
    renderCurrentSession();
  }

  saveSessions();
  renderSessionList();
  input.focus();
}

function persistMessage(role, text) {
  const session = getCurrentSession();
  session.messages.push({ role, text });
  session.updatedAt = Date.now();

  if (role === "user" && isUntitledSession(session)) {
    session.title = createTitleFromQuestion(text);
  }

  saveSessions();
  renderSessionList();
}

function getCurrentSession() {
  let session = sessions.find((item) => item.id === sessionId);
  if (!session) {
    session = createSession(sessionId);
    sessions.unshift(session);
    saveSessions();
  }
  return session;
}

function createSession(id = crypto.randomUUID(), inHistory = false) {
  return {
    id,
    title: UNTITLED,
    messages: createWelcomeMessages(),
    inHistory,
    createdAt: Date.now(),
    updatedAt: Date.now()
  };
}

function createWelcomeMessages() {
  return [{ role: "assistant", text: WELCOME_TEXT }];
}

function loadSessions() {
  try {
    const parsed = JSON.parse(localStorage.getItem(SESSIONS_KEY) || "[]");
    if (!Array.isArray(parsed)) {
      return [];
    }

    return parsed
      .filter((session) => session?.id && Array.isArray(session.messages))
      .map((session) => ({
        id: session.id,
        title: session.title || UNTITLED,
        messages: session.messages.filter((message) => message?.role && typeof message.text === "string"),
        inHistory: session.inHistory ?? true,
        createdAt: session.createdAt || Date.now(),
        updatedAt: session.updatedAt || Date.now()
      }));
  } catch (error) {
    return [];
  }
}

function saveSessions() {
  localStorage.setItem(SESSIONS_KEY, JSON.stringify(sessions.slice(0, 30)));
}

function getInitialSessionId() {
  const existingId = localStorage.getItem(SESSION_KEY);
  if (existingId && sessions.some((session) => session.id === existingId)) {
    return existingId;
  }

  const firstHistory = getHistorySessions()[0];
  if (firstHistory) {
    localStorage.setItem(SESSION_KEY, firstHistory.id);
    return firstHistory.id;
  }

  const session = createSession(existingId || crypto.randomUUID());
  sessions.unshift(session);
  localStorage.setItem(SESSION_KEY, session.id);
  saveSessions();
  return session.id;
}

function archiveCurrentSessionIfNeeded() {
  const session = getCurrentSession();
  if (!hasConversationContent(session)) {
    return;
  }

  session.inHistory = true;
  session.updatedAt = Date.now();
  moveCurrentSessionToTop();
}

function removeEmptyDraftSessions() {
  sessions = sessions.filter((session) => session.inHistory || session.id === sessionId || hasConversationContent(session));
  sessions = sessions.filter((session) => session.id === sessionId || session.inHistory);
}

function moveCurrentSessionToTop() {
  const currentIndex = sessions.findIndex((session) => session.id === sessionId);
  if (currentIndex <= 0) {
    return;
  }
  const [currentSession] = sessions.splice(currentIndex, 1);
  sessions.unshift(currentSession);
}

function getHistorySessions() {
  return sessions.filter((session) => session.inHistory);
}

function hasConversationContent(session) {
  return session.messages.some((message) => {
    if (message.role === "user") {
      return Boolean(message.text?.trim());
    }
    return message.role === "assistant" && message.text?.trim() && message.text !== WELCOME_TEXT;
  });
}

function isUntitledSession(session) {
  return !session.title || session.title === UNTITLED;
}

function createTitleFromQuestion(question) {
  const compact = question.replace(/\s+/g, " ").trim();
  return compact.length > 18 ? `${compact.slice(0, 18)}...` : compact || UNTITLED;
}

function getSessionDisplayTitle(session) {
  const firstUserMessage = session.messages.find((message) => message.role === "user" && message.text?.trim());
  if (firstUserMessage) {
    return createTitleFromQuestion(firstUserMessage.text);
  }

  if (session.title && !isUntitledSession(session)) {
    return session.title;
  }

  return `会话 ${session.id.slice(0, 8)}`;
}

function renderMarkdown(markdown) {
  return DOMPurify.sanitize(marked.parse(markdown || ""));
}

function escapeMarkdown(text) {
  return String(text).replace(/[\\`*_{}[\]()#+\-.!|>]/g, "\\$&");
}

function formatFileSize(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function setChatMode(nextMode) {
  chatMode = nextMode === "stream" ? "stream" : "standard";
  localStorage.setItem(MODE_KEY, chatMode);

  modeButtons.forEach((button) => {
    const isActive = button.dataset.mode === chatMode;
    button.classList.toggle("is-active", isActive);
    button.setAttribute("aria-pressed", String(isActive));
  });

  endpointText.textContent = chatMode === "stream" ? "/api/chat_stream" : "/api/chat";
}

function setSending(nextState) {
  isSending = nextState;
  sendButton.disabled = nextState;
  uploadButton.disabled = nextState;
  aiOpsButton.disabled = nextState;
  clearButton.disabled = nextState;
  newChatButton.disabled = nextState;
  input.disabled = nextState;
  document.body.classList.toggle("is-busy", nextState);
  document.body.setAttribute("aria-busy", String(nextState));
  modeButtons.forEach((button) => {
    button.disabled = nextState;
  });
}

function setApiStatus(isHealthy) {
  apiStatus.classList.toggle("is-error", !isHealthy);
  apiStatus.lastChild.textContent = isHealthy ? " 后端连接正常" : " 后端请求异常";
}

function resizeTextarea() {
  input.style.height = "auto";
  input.style.height = `${Math.min(input.scrollHeight, 180)}px`;
}

function scrollToBottom() {
  messages.scrollTop = messages.scrollHeight;
}

setChatMode(chatMode);
renderSessionList();
renderCurrentSession();
resizeTextarea();
