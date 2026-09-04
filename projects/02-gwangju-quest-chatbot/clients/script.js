const API_BASE = "http://localhost:8000";

const fallbackQuests = [
  { id: "fallback-taste", title: "취향 탐색", description: "서로 가고 싶은 광주 장소를 하나씩 고르고 이유를 말해보세요.", difficulty: "NORMAL", xp: 100, location: "광주", status: "active" },
  { id: "fallback-menu", title: "동명동 메뉴 매칭", description: "상대방에게 어울릴 것 같은 카페 메뉴를 하나씩 골라주세요.", difficulty: "HARD", xp: 150, location: "동명동", status: "active" },
  { id: "fallback-photo", title: "양림동 포토 파트너", description: "서로의 여행 분위기가 드러나는 사진을 한 장씩 찍어주세요.", difficulty: "NORMAL", xp: 100, location: "양림동", status: "active" }
];

const secretTemplates = [
  name => `${name}에게 들키지 않고 광주다운 배경에서 사진을 3장 찍게 만드세요.`,
  name => `${name}이 광주 음식을 하나 직접 고르도록 자연스럽게 유도하세요.`,
  name => `${name}에게 오늘 가장 마음에 든 순간을 먼저 말하게 해보세요.`
];

const state = {
  xp: 0,
  level: 1,
  completedQuestCount: 0,
  fallbackQuestIndex: 0,
  players: ["진영", "민수"],
  conversationHistory: [],
  travelProfile: {
    players: ["진영", "민수"],
    playerCount: 2,
    companionType: null,
    duration: null,
    transport: null,
    preferences: [],
    walkingPreference: null,
    weather: null,
    currentArea: null
  },
  currentQuest: { ...fallbackQuests[0] },
  completedQuests: [],
  secretQuests: []
};
let sending = false;

const $ = selector => document.querySelector(selector);
const chatWindow = $("#chat-window");

function scrollChatToBottom(behavior = "smooth") {
  requestAnimationFrame(() => {
    chatWindow.scrollTo({ top: chatWindow.scrollHeight, behavior });
  });
}

function rememberConversation(userMessage, assistantMessage) {
  state.conversationHistory.push(
    { role: "user", content: userMessage },
    { role: "assistant", content: assistantMessage }
  );
  state.conversationHistory = state.conversationHistory.slice(-12);
}

function updateTravelProfile(updates) {
  if (!updates || typeof updates !== "object") return;
  Object.entries(updates).forEach(([key, value]) => {
    if (value === null || value === undefined || value === "") return;
    if (key === "preferences" && Array.isArray(value)) {
      state.travelProfile.preferences = [...new Set([...state.travelProfile.preferences, ...value])];
      return;
    }
    if (Array.isArray(value) && value.length === 0) return;
    if (key in state.travelProfile) state.travelProfile[key] = value;
  });
  renderTravelProfile();
}

function renderTravelProfile() {
  const profile = state.travelProfile;
  const rows = [
    ["👥", profile.companionType || (profile.playerCount === 1 ? "혼자" : `${profile.playerCount}명`)],
    ["⏰", profile.duration],
    ["🚇", profile.transport],
    ["❤️", profile.preferences.length ? profile.preferences.join(" · ") : null],
    ["🚶", profile.walkingPreference],
    ["☁️", profile.weather],
    ["📍", profile.currentArea]
  ].filter(([, value]) => value);
  const container = $("#trip-profile-content");
  container.replaceChildren();
  if (!rows.length) {
    const empty = document.createElement("span");
    empty.className = "profile-empty";
    empty.textContent = "아직 파악 중";
    container.appendChild(empty);
    return;
  }
  rows.forEach(([icon, value]) => {
    const item = document.createElement("div");
    item.className = "profile-item";
    const iconElement = document.createElement("span");
    const valueElement = document.createElement("span");
    iconElement.textContent = icon;
    valueElement.textContent = value;
    item.append(iconElement, valueElement);
    container.appendChild(item);
  });
}

function normalizeQuest(quest) {
  if (!quest || !quest.title || !quest.description) return null;
  const difficulty = ["EASY", "NORMAL", "HARD", "SECRET"].includes(String(quest.difficulty).toUpperCase())
    ? String(quest.difficulty).toUpperCase()
    : "NORMAL";
  const xpByDifficulty = { EASY: 50, NORMAL: 100, HARD: 150, SECRET: 200 };
  const requestedXp = Number(quest.xp);
  return {
    id: quest.id || `quest-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    title: String(quest.title).slice(0, 80),
    description: String(quest.description).slice(0, 500),
    difficulty,
    xp: [50, 100, 150, 200].includes(requestedXp) ? requestedXp : xpByDifficulty[difficulty],
    location: quest.location ? String(quest.location).slice(0, 80) : "광주",
    status: "active"
  };
}

function syncQuest(quest) {
  const normalized = normalizeQuest(quest);
  if (!normalized) return false;
  const alreadyCompleted = state.completedQuests.some(completed =>
    completed.id === normalized.id ||
    (completed.title === normalized.title && completed.location === normalized.location)
  );
  if (alreadyCompleted) return false;
  state.currentQuest = normalized;
  renderGame();
  return true;
}

function levelForXp(xp) {
  if (xp >= 1000) return 5;
  if (xp >= 700) return 4;
  if (xp >= 400) return 3;
  if (xp >= 200) return 2;
  return 1;
}

function levelRange(level) {
  return [{ min: 0, max: 200 }, { min: 200, max: 400 }, { min: 400, max: 700 }, { min: 700, max: 1000 }, { min: 1000, max: 1000 }][level - 1];
}

function renderGame() {
  state.level = levelForXp(state.xp);
  const range = levelRange(state.level);
  const progress = state.level === 5 ? 100 : ((state.xp - range.min) / (range.max - range.min)) * 100;
  const quest = state.currentQuest?.status === "active" ? state.currentQuest : null;
  $("#level-display").textContent = `LV. ${state.level}`;
  $("#xp-label").textContent = state.level === 5 ? `${state.xp} XP · MAX` : `${state.xp} / ${range.max} XP`;
  $("#xp-bar").style.width = `${Math.max(0, Math.min(progress, 100))}%`;
  $("#quest-count").textContent = state.completedQuestCount;
  $("#quest-title").textContent = quest?.title || "다음 Quest 대기 중";
  $("#quest-location").textContent = quest?.location || "Quest Master에게 다음 미션을 요청하세요";
  $("#quest-description").textContent = quest?.description || "채팅에 ‘다음 미션 줘’라고 입력하면 현재 여행 조건에 맞는 Quest를 받을 수 있어요.";
  $("#quest-difficulty").textContent = quest ? `${quest.difficulty} · ${quest.xp} XP` : "QUEST CLEAR";
  $("#complete-quest").disabled = !quest;
}

function addMessage(type, text, source = "") {
  const article = document.createElement("article");
  article.className = `message ${type}`;
  const avatar = document.createElement("div");
  avatar.className = "avatar";
  avatar.textContent = type === "bot" ? "Q" : "P";
  const bubble = document.createElement("div");
  bubble.className = "bubble";
  const speaker = document.createElement("span");
  speaker.className = "speaker";
  speaker.textContent = type === "bot" ? "QUEST MASTER" : state.players.join(" & ");
  bubble.append(speaker, document.createTextNode(text));
  if (type === "bot" && source) {
    const sourceTag = document.createElement("div");
    sourceTag.className = "source-tag";
    sourceTag.textContent = `참고: ${source}`;
    bubble.appendChild(sourceTag);
  }
  article.append(avatar, bubble);
  chatWindow.appendChild(article);
  scrollChatToBottom(type === "bot" ? "smooth" : "auto");
  return article;
}

function errorMessage(data, status) {
  if (data?.detail) return data.detail;
  if (data?.message) return data.message;
  return `서버 요청에 실패했습니다. (HTTP ${status})`;
}

async function sendMessage(messageOverride = "") {
  const input = $("#user-input");
  const message = (messageOverride || input.value).trim();
  if (!message || sending) return;

  sending = true;
  $("#send-button").disabled = true;
  input.disabled = true;
  const history = state.conversationHistory.slice(-10);
  addMessage("user", message);
  input.value = "";
  const typing = addMessage("bot", "퀘스트를 설계하고 있어요...");
  typing.classList.add("typing");

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 45000);
  try {
    const quest = state.currentQuest;
    const response = await fetch(`${API_BASE}/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        message,
        use_rag: $("#rag-toggle").checked,
        history,
        profile: state.travelProfile,
        completed_quests: state.completedQuests,
        game_state: {
          players: state.players,
          xp: state.xp,
          level: state.level,
          completedQuestCount: state.completedQuestCount,
          currentQuest: quest?.status === "active" ? quest.title : "없음",
          currentQuestSource: quest?.id?.startsWith("fallback-") ? "fallback" : (quest ? "ai" : "none")
        }
      }),
      signal: controller.signal
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(errorMessage(data, response.status));
    typing.remove();
    const answer = data.response || data.answer || "응답 내용이 없습니다.";
    addMessage("bot", answer, data.source);
    rememberConversation(message, answer);
    updateTravelProfile(data.profile_updates);
    if (data.intent === "QUEST_COMPLETE") {
      completeQuest("natural");
    } else if (data.quest) {
      syncQuest(data.quest);
    }
    if (data.intent === "TRIP_END") showToast("여행 종료 요청을 확인했습니다.");
  } catch (error) {
    typing.remove();
    const text = error.name === "AbortError" ? "응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요." : `오류: ${error.message}`;
    addMessage("bot", text);
  } finally {
    clearTimeout(timeout);
    sending = false;
    $("#send-button").disabled = false;
    input.disabled = false;
    input.focus();
  }
}

function completeQuest(trigger = "button") {
  const quest = state.currentQuest;
  if (!quest || quest.status !== "active") {
    if (trigger === "natural") showToast("완료할 활성 Quest가 없습니다.");
    return false;
  }
  if (state.completedQuests.some(completed => completed.id === quest.id)) return false;
  quest.status = "completed";
  state.completedQuests.push({ id: quest.id, title: quest.title, location: quest.location, xp: quest.xp });
  state.xp += quest.xp;
  state.completedQuestCount += 1;
  const previousLevel = state.level;
  state.currentQuest = null;
  renderGame();
  const levelUp = state.level > previousLevel ? `\nLEVEL UP! LV.${state.level}에 도달했습니다.` : "";
  addMessage("bot", `🎉 QUEST CLEAR!\n'${quest.title}' 미션을 완료했습니다.\n+${quest.xp} XP${levelUp}\n다음 미션이 필요하면 채팅으로 요청해보세요.`);
  showToast(`+${quest.xp} XP 획득`);
  return true;
}

function savePlayers() {
  const first = $("#player-one").value.trim() || "Player 1";
  const second = $("#player-two").value.trim() || "Player 2";
  state.players = [first, second];
  state.travelProfile.players = [...state.players];
  state.travelProfile.playerCount = state.players.length;
  const firstTemplate = secretTemplates[Math.floor(Math.random() * secretTemplates.length)];
  let secondTemplate = secretTemplates[Math.floor(Math.random() * secretTemplates.length)];
  if (secretTemplates.length > 1 && secondTemplate === firstTemplate) {
    secondTemplate = secretTemplates[(secretTemplates.indexOf(firstTemplate) + 1) % secretTemplates.length];
  }
  state.secretQuests = [
    { id: `secret-${Date.now()}-1`, player: first, description: firstTemplate(second), xp: 200, status: "active" },
    { id: `secret-${Date.now()}-2`, player: second, description: secondTemplate(first), xp: 200, status: "active" }
  ];
  $("#secret-quest-one").textContent = `${first}의 미션: ${state.secretQuests[0].description} (+200 XP)`;
  $("#secret-quest-two").textContent = `${second}의 미션: ${state.secretQuests[1].description} (+200 XP)`;
  renderTravelProfile();
  showToast(`${first} · ${second} 파티가 저장되었습니다.`);
}

async function uploadDocument() {
  const file = $("#document-file").files[0];
  if (!file) return showToast("업로드할 파일을 선택해주세요.");
  const formData = new FormData();
  formData.append("file", file);
  $("#upload-button").disabled = true;
  try {
    const response = await fetch(`${API_BASE}/upload`, { method: "POST", body: formData });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(errorMessage(data, response.status));
    addMessage("bot", data.message);
    showToast("문서 업로드 완료");
  } catch (error) {
    addMessage("bot", `문서 업로드 오류: ${error.message}`);
  } finally {
    $("#upload-button").disabled = false;
  }
}

function showToast(text) {
  const toast = $("#toast");
  toast.textContent = text;
  toast.classList.add("show");
  setTimeout(() => toast.classList.remove("show"), 2200);
}

async function checkHealth() {
  const element = $("#server-state");
  try {
    const response = await fetch(`${API_BASE}/health`);
    const data = await response.json();
    element.className = `server-state ${data.ai_ready ? "online" : "offline"}`;
    element.innerHTML = `<span></span>${data.ai_ready ? "AI ONLINE" : "설정 필요"}`;
  } catch {
    element.className = "server-state offline";
    element.innerHTML = "<span></span>서버 오프라인";
  }
}

$("#chat-form").addEventListener("submit", event => { event.preventDefault(); sendMessage(); });
$("#complete-quest").addEventListener("click", () => completeQuest("button"));
$("#save-players").addEventListener("click", savePlayers);
$("#upload-button").addEventListener("click", uploadDocument);
$("#upload-toggle").addEventListener("click", event => {
  const panel = $("#upload-panel");
  panel.hidden = !panel.hidden;
  event.currentTarget.setAttribute("aria-expanded", String(!panel.hidden));
});
$("#secret-toggle").addEventListener("click", event => {
  const expanded = event.currentTarget.getAttribute("aria-expanded") === "true";
  event.currentTarget.setAttribute("aria-expanded", String(!expanded));
});

renderGame();
savePlayers();
renderTravelProfile();
checkHealth();
