// F104 여행 계획 (경로·일정·경비) + F114 경로 주변 전기차 충전소
import { $, debounce, escapeHtml, formatDate, formatWon, setFieldError, toast, uid } from "../core/util.js";
import { CONTENT_TYPES, contentTypeLabel, getSidoList, searchSpots } from "../core/tour-api.js";
import { createMap, distanceKm, loadKakaoMap, showMapNotice } from "../core/kakao.js";
import { CONNECTORS, STATUS, evSource, findChargersNear } from "../core/ev-api.js";
import { EXPENSE_CATEGORIES, addSpotToDraft, defaultCategory, emptyPlan, loadDraft, saveDraft } from "../core/plan-store.js";
import { currentUser } from "../core/auth.js";
import { plans, prefs } from "../core/storage.js";
import { getRoute, hasRouteKey } from "../core/route.js";
import { getDayForecast, weatherInfo } from "../core/weather.js";

const NEAR_KM = 1; // 정류지 반경 몇 km 안의 충전소를 볼지
const CHARGE_AT = 0.7; // 주행가능거리의 70%를 넘기기 전에 충전을 권한다

// 예전에 저장한 계획에는 새 필드가 없을 수 있으므로 기본값을 채운다
const normalize = (p) => ({
  ...emptyPlan(),
  ...p,
  stops: p.stops.map((s) => ({ category: defaultCategory(s.contentTypeId), ...s })),
});

let plan = normalize(loadDraft());
let mapView = null;
let evLayer = null;
let findResults = [];
const chargerCache = new Map(); // stop.key + connector → 충전소 목록
let route = null; // 실제 도로 경로 (카카오모빌리티, REST 키가 있을 때만)
let routeFor = ""; // route 가 어떤 정류지 순서에 대한 결과인지
let sunset = null; // 출발일 첫 정류지 일몰 시각 "HH:MM"

/* =========================================================
   계획 요약 계산
   ========================================================= */
const stopsKey = () => plan.stops.map((s) => `${s.key}@${s.x},${s.y}`).join("|");
const roadRoute = () => (route && routeFor === stopsKey() ? route : null);

/** 구간 거리(km). 도로 경로가 있으면 도로 기준, 없으면 직선거리 */
const legs = () =>
  roadRoute()?.legs.map((l) => l.distanceKm) ??
  plan.stops.slice(1).map((s, i) => {
    const prev = plan.stops[i];
    return prev.x && s.x ? distanceKm(prev, s) : 0;
  });

const totalKm = () => legs().reduce((a, b) => a + b, 0);
const chargingCost = () => Math.round((totalKm() / (plan.efficiency || 5)) * (plan.pricePerKwh || 0));

const formatDuration = (min) => (min >= 60 ? `${Math.floor(min / 60)}시간 ${min % 60}분` : `${min}분`);

/** 정류지 순서가 바뀌었을 때만 도로 경로를 다시 조회 */
const refreshRoute = debounce(async () => {
  const key = stopsKey();
  if (!hasRouteKey() || plan.stops.length < 2 || key === routeFor) return;
  const result = await getRoute(plan.stops);
  if (key !== stopsKey()) return; // 기다리는 동안 계획이 바뀌었으면 버린다
  route = result;
  routeFor = key;
  if (result) renderStops();
}, 400);

/** 누적 거리가 주행가능거리의 70%를 넘기 직전 정류지 = 충전 추천 지점 */
const chargeSuggestions = () => {
  const limit = plan.rangeKm * CHARGE_AT;
  const result = new Set();
  let since = 0;
  legs().forEach((d, i) => {
    if (since + d > limit) {
      result.add(i); // i 번째 정류지(다음 구간 출발 전)에서 충전
      since = 0;
    }
    since += d;
  });
  return result;
};

/* =========================================================
   렌더링
   ========================================================= */
const stopHtml = (s, i, suggest) => `
  <li class="stop" data-key="${s.key}" draggable="true">
    <button type="button" class="drag-handle" aria-label="${escapeHtml(s.title)} 순서 이동 (Alt+위/아래)">⋮⋮</button>
    <span class="stop-no" aria-hidden="true">${i + 1}</span>
    <div class="min-w-0">
      <p class="stop-name text-truncate">${escapeHtml(s.title)}</p>
      <p class="small text-body-secondary mb-0 text-truncate">${escapeHtml(s.address || contentTypeLabel(s.contentTypeId))}</p>
      <p class="ev-line mb-0 ${suggest ? "is-warn" : ""}" data-ev="${s.key}">⚡ 충전소 확인 중…</p>
      <p class="sun-line mb-0" data-sun="${s.key}"></p>
      <div class="stop-fields">
        <label class="visually-hidden" for="time-${s.key}">방문 시간</label>
        <input id="time-${s.key}" type="time" class="form-control" data-field="time" value="${escapeHtml(s.time)}" />
        <label class="visually-hidden" for="cat-${s.key}">경비 항목</label>
        <select id="cat-${s.key}" class="form-select" data-field="category">
          ${EXPENSE_CATEGORIES.map((c) => `<option value="${c.id}" ${c.id === s.category ? "selected" : ""}>${c.label}</option>`).join("")}
        </select>
        <label class="visually-hidden" for="cost-${s.key}">경비</label>
        <input id="cost-${s.key}" type="number" class="form-control" data-field="cost" min="0" step="1000" placeholder="원" value="${s.cost || ""}" />
      </div>
      <label class="visually-hidden" for="memo-${s.key}">메모</label>
      <input id="memo-${s.key}" class="form-control form-control-sm mt-1" data-field="memo" maxlength="60" placeholder="메모" value="${escapeHtml(s.memo)}" />
    </div>
    <button type="button" class="btn btn-sm btn-link text-body-secondary" data-remove="${s.key}" aria-label="${escapeHtml(s.title)} 삭제">✕</button>
  </li>`;

const renderStops = () => {
  const list = $("#stop-list");
  const ls = legs();
  const durations = roadRoute()?.legs.map((l) => l.durationMin);
  const legText = (i) => `↓ ${ls[i].toFixed(1)}km${durations ? ` · 약 ${formatDuration(durations[i])}` : ""}`;
  const suggest = chargeSuggestions();
  if (plan.stops.length === 0) {
    list.innerHTML = `<li class="empty-state">아직 담은 장소가 없습니다.<br><button class="btn btn-sm btn-outline-primary mt-2" type="button" id="go-find">장소 찾으러 가기</button></li>`;
    $("#go-find")?.addEventListener("click", () => bootstrap.Tab.getOrCreateInstance("#tab-find").show());
  } else {
    list.innerHTML = plan.stops
      .map((s, i) => stopHtml(s, i, suggest.has(i)) + (i < ls.length ? `<li class="leg" aria-hidden="true">${legText(i)}</li>` : ""))
      .join("");
  }
  $("#stop-count").textContent = plan.stops.length;
  renderSummary();
  renderMap();
  renderChargers();
  renderWeather();
  refreshRoute();
};

/** 항목별 경비: 충전비(이동 거리 ÷ 전비 × 단가) + 장소별 입력 경비 */
const expenseRows = () => {
  const rows = [{ label: "⚡ 충전비 (자동 계산)", amount: chargingCost() }];
  EXPENSE_CATEGORIES.forEach((c) =>
    rows.push({ label: c.label, amount: plan.stops.filter((s) => s.category === c.id).reduce((a, s) => a + (Number(s.cost) || 0), 0) })
  );
  return rows;
};

const renderSummary = () => {
  const total = totalKm();
  const road = roadRoute();
  const basis = road ? `도로 기준 · 약 ${formatDuration(road.durationMin)}` : "직선거리 기준";
  const rows = expenseRows();
  const sum = rows.reduce((a, r) => a + r.amount, 0);
  $("#sum-main").textContent = `총 이동 ${total.toFixed(1)}km (${basis}) · 예상 경비 ${formatWon(sum)}`;
  $("#sum-expense").innerHTML =
    rows
      .filter((r, i) => i === 0 || r.amount > 0)
      .map((r) => `<tr><th scope="row" class="fw-normal">${escapeHtml(r.label)}</th><td class="text-end">${formatWon(r.amount)}</td></tr>`)
      .join("") + `<tr class="fw-bold"><th scope="row">합계</th><td class="text-end">${formatWon(sum)}</td></tr>`;

  const sumRange = $("#sum-range");
  const suggest = chargeSuggestions();
  if (plan.stops.length < 2) {
    sumRange.textContent = "장소를 2곳 이상 담으면 이동 거리와 충전 필요 여부를 계산합니다.";
    sumRange.className = "small mb-2 text-body-secondary";
  } else if (suggest.size === 0) {
    sumRange.textContent = `주행가능거리 ${plan.rangeKm}km 기준 — 중간 충전 없이 이동할 수 있어요.`;
    sumRange.className = "small mb-2 text-primary";
  } else {
    const names = [...suggest].map((i) => `${i + 1}번 ${plan.stops[i].title}`).join(", ");
    sumRange.textContent = `주행가능거리의 ${CHARGE_AT * 100}%를 넘기 전에 충전을 권해요: ${names}`;
    sumRange.className = "small mb-2 text-danger";
  }
};

/** F111·F113 출발일 날씨와 일몰 시각 (첫 정류지 기준), 일몰 이후 방문 경고 */
const renderWeather = debounce(async () => {
  const box = $("#sum-weather");
  const first = plan.stops.find((s) => s.x && s.y);
  sunset = null;
  if (!first || !plan.startDate) {
    box.textContent = "";
  } else {
    try {
      const day = await getDayForecast(first, plan.startDate);
      if (day) {
        const w = weatherInfo(day.code);
        sunset = day.sunset;
        box.textContent = `${w.icon} 출발일 ${w.label} ${day.max}°/${day.min}° · 강수확률 ${day.rain ?? 0}% · 일출 ${day.sunrise} · 일몰 ${day.sunset}`;
      } else {
        box.textContent = "출발일이 예보 범위(오늘부터 16일) 밖이라 날씨를 표시하지 않습니다.";
      }
    } catch {
      box.textContent = "날씨 정보를 불러오지 못했습니다.";
    }
  }
  plan.stops.forEach((s) => {
    const el = document.querySelector(`[data-sun="${s.key}"]`);
    if (el) el.textContent = sunset && s.time && s.time >= sunset ? `🌇 일몰(${sunset}) 이후 도착 — 야경 명소가 아니라면 시간을 앞당겨 보세요` : "";
  });
}, 300);

const renderMap = () => {
  if (!mapView) return;
  mapView.render(plan.stops, { numbered: true, path: true, pathPoints: roadRoute()?.path });
};

/** 정류지별 주변 충전소를 조회해 목록 문구와 지도 레이어를 갱신 */
const renderChargers = debounce(async () => {
  const connector = $("#ev-connector").value;
  const show = $("#ev-toggle").checked;
  const all = new Map();
  await Promise.all(
    plan.stops.map(async (s) => {
      const line = document.querySelector(`[data-ev="${s.key}"]`);
      if (!s.x || !s.y) {
        if (line) line.textContent = "⚡ 위치 정보가 없어 충전소를 찾을 수 없습니다";
        return;
      }
      const cacheKey = `${s.key}|${connector}`;
      if (!chargerCache.has(cacheKey)) {
        const regionCode = mapView ? await mapView.regionCode(s) : "";
        chargerCache.set(cacheKey, await findChargersNear(s, { radiusKm: NEAR_KM, regionCode, connector }));
      }
      const stations = chargerCache.get(cacheKey);
      stations.forEach((st) => all.set(st.id, st));
      if (!line) return;
      const fast = stations.reduce((a, st) => a + st.fastCount, 0);
      const avail = stations.reduce((a, st) => a + st.availableCount, 0);
      line.textContent = stations.length
        ? `⚡ ${NEAR_KM}km 이내 충전소 ${stations.length}곳 · 급속 ${fast}기 · 대기 ${avail}기${line.classList.contains("is-warn") ? " — 여기서 충전 추천" : ""}`
        : `⚡ ${NEAR_KM}km 이내 충전소 없음${line.classList.contains("is-warn") ? " — 출발 전 충전 필요" : ""}`;
    })
  );
  $("#ev-source").textContent = evSource.lastUsed === "api" ? "실시간" : "예시 데이터";
  $("#ev-source").title = evSource.lastError ?? "";
  if (!evLayer) return;
  if (!show) return evLayer.clear();
  evLayer.set(
    [...all.values()].map((st) => ({
      ...st,
      html: `<div class="map-iw"><strong>${escapeHtml(st.title)}</strong><br>
        <span>${escapeHtml(st.address)}</span><br>
        ${st.chargers.map((c) => `${escapeHtml(c.typeLabel)} ${c.output ? `${c.output}kW` : ""} · ${STATUS[c.stat]?.label ?? "-"}`).join("<br>")}</div>`,
    }))
  );
}, 250);

/* =========================================================
   편집: 입력 / 삭제 / 드래그 앤 드롭
   ========================================================= */
const persist = () => saveDraft(plan);

$("#stop-list").addEventListener("input", (e) => {
  const field = e.target.dataset.field;
  if (!field) return;
  const key = e.target.closest(".stop").dataset.key;
  const stop = plan.stops.find((s) => s.key === key);
  stop[field] = field === "cost" ? Math.max(0, Number(e.target.value) || 0) : e.target.value;
  persist();
  if (field === "cost" || field === "category") renderSummary();
  if (field === "time") renderWeather();
});

$("#stop-list").addEventListener("click", (e) => {
  const key = e.target.dataset.remove;
  if (!key) return;
  plan.stops = plan.stops.filter((s) => s.key !== key);
  persist();
  renderStops();
});

const moveStop = (from, to) => {
  if (to < 0 || to >= plan.stops.length || from === to) return;
  const [item] = plan.stops.splice(from, 1);
  plan.stops.splice(to, 0, item);
  persist();
  renderStops();
};

let dragKey = null;
const indexOf = (key) => plan.stops.findIndex((s) => s.key === key);
const list = $("#stop-list");
list.addEventListener("dragstart", (e) => {
  const li = e.target.closest(".stop");
  if (!li) return;
  dragKey = li.dataset.key;
  li.classList.add("is-dragging");
  e.dataTransfer.effectAllowed = "move";
  e.dataTransfer.setData("text/plain", dragKey); // Firefox 는 데이터가 있어야 드래그가 시작된다
});
list.addEventListener("dragover", (e) => {
  const li = e.target.closest(".stop");
  if (!li || !dragKey) return;
  e.preventDefault();
  list.querySelectorAll(".is-over").forEach((el) => el.classList.remove("is-over"));
  li.classList.add("is-over");
});
list.addEventListener("drop", (e) => {
  const li = e.target.closest(".stop");
  if (!li || !dragKey) return;
  e.preventDefault();
  moveStop(indexOf(dragKey), indexOf(li.dataset.key));
});
list.addEventListener("dragend", () => {
  dragKey = null;
  list.querySelectorAll(".is-over, .is-dragging").forEach((el) => el.classList.remove("is-over", "is-dragging"));
});
// 키보드로도 순서 변경 (접근성)
list.addEventListener("keydown", (e) => {
  if (!e.altKey || !e.target.classList.contains("drag-handle")) return;
  const key = e.target.closest(".stop").dataset.key;
  const idx = indexOf(key);
  if (e.key === "ArrowUp" || e.key === "ArrowDown") {
    e.preventDefault();
    moveStop(idx, idx + (e.key === "ArrowUp" ? -1 : 1));
    document.querySelector(`.stop[data-key="${key}"] .drag-handle`)?.focus();
  }
});

/* =========================================================
   메타 정보 / 저장 / 저장 목록
   ========================================================= */
const fillMeta = () => {
  $("#plan-title").value = plan.title;
  $("#plan-date").value = plan.startDate;
  $("#plan-range").value = plan.rangeKm;
  $("#plan-eff").value = plan.efficiency;
  $("#plan-price").value = plan.pricePerKwh;
};
$("#plan-title").addEventListener("input", (e) => { plan.title = e.target.value; persist(); });
$("#plan-date").addEventListener("change", (e) => { plan.startDate = e.target.value; persist(); renderWeather(); });
$("#plan-eff").addEventListener("change", (e) => {
  plan.efficiency = Math.min(10, Math.max(2, Number(e.target.value) || 5));
  e.target.value = plan.efficiency;
  persist();
  renderSummary();
});
$("#plan-price").addEventListener("change", (e) => {
  plan.pricePerKwh = Math.min(1000, Math.max(0, Math.round(Number(e.target.value) || 0)));
  e.target.value = plan.pricePerKwh;
  persist();
  renderSummary();
});

/* ---------- Markdown 내보내기 ---------- */
const toMarkdown = () => {
  const ls = legs();
  const road = roadRoute();
  const rows = expenseRows();
  const sum = rows.reduce((a, r) => a + r.amount, 0);
  const suggest = chargeSuggestions();
  const esc = (t) => String(t ?? "").replace(/\|/g, "\\|").replace(/\r?\n/g, " ");
  const lines = [
    `# ${esc(plan.title.trim() || "여행 계획")}`,
    "",
    `- 출발일: ${plan.startDate}`,
    `- 총 이동: ${totalKm().toFixed(1)}km (${road ? `도로 기준, 약 ${formatDuration(road.durationMin)}` : "직선거리 기준"})`,
    `- 주행가능거리: ${plan.rangeKm}km · 전비 ${plan.efficiency}km/kWh · 충전 단가 ${plan.pricePerKwh}원/kWh`,
    $("#sum-weather").textContent ? `- 날씨: ${$("#sum-weather").textContent}` : null,
    "",
    "## 일정",
    "",
    "| 순서 | 시간 | 장소 | 주소 | 경비 | 메모 | 충전 |",
    "| --- | --- | --- | --- | --- | --- | --- |",
    ...plan.stops.map((s, i) => {
      const ev = document.querySelector(`[data-ev="${s.key}"]`)?.textContent.replace(/^⚡\s*/, "") ?? "";
      const link = s.x ? `[지도](https://map.kakao.com/link/map/${encodeURIComponent(s.title)},${s.y},${s.x})` : "";
      return `| ${i + 1} | ${esc(s.time) || "-"} | ${esc(s.title)} ${link} | ${esc(s.address)} | ${formatWon(s.cost)} | ${esc(s.memo) || "-"} | ${suggest.has(i) ? "**충전 추천** · " : ""}${esc(ev)} |` +
        (i < ls.length ? `\n| ↓ | | ${ls[i].toFixed(1)}km 이동 | | | | |` : "");
    }),
    "",
    "## 예상 경비",
    "",
    "| 항목 | 금액 |",
    "| --- | ---: |",
    ...rows.filter((r, i) => i === 0 || r.amount > 0).map((r) => `| ${r.label} | ${formatWon(r.amount)} |`),
    `| **합계** | **${formatWon(sum)}** |`,
    "",
    `> EnjoyTrip EV 에서 ${formatDate(new Date(), true)} 에 내보냄. 충전소 정보는 ${evSource.lastUsed === "api" ? "한국환경공단 실시간" : "예시"} 데이터 기준입니다.`,
  ];
  return lines.filter((l) => l !== null).join("\n");
};

$("#btn-export").addEventListener("click", () => {
  if (plan.stops.length === 0) return toast("내보낼 장소가 없습니다.", "danger");
  const blob = new Blob([toMarkdown()], { type: "text/markdown;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  // 파일 이름에 쓸 수 없는 문자는 바꾼다
  a.download = `${(plan.title.trim() || "여행계획").replace(/[\\/:*?"<>|]/g, "_")}_${plan.startDate}.md`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
});
$("#plan-range").addEventListener("change", (e) => {
  const v = Math.min(1000, Math.max(50, Number(e.target.value) || 300));
  e.target.value = v;
  plan.rangeKm = v;
  persist();
  renderStops();
});

$("#btn-save").addEventListener("click", () => {
  const user = currentUser();
  if (!user) {
    if (confirm("계획을 저장하려면 로그인이 필요합니다. 로그인 페이지로 이동할까요?\n(작성 중인 계획은 그대로 남아 있습니다)")) {
      location.href = "login.html?redirect=plan.html";
    }
    return;
  }
  if (!setFieldError($("#plan-title"), plan.title.trim() ? "" : "여행 제목을 입력하세요.")) return $("#plan-title").focus();
  if (plan.stops.length === 0) return toast("장소를 1곳 이상 담아 주세요.", "danger");
  const now = new Date().toISOString();
  const record = { ...plan, title: plan.title.trim(), userId: user.id, updatedAt: now };
  try {
    if (plan.id && plans.get(plan.id)?.userId === user.id) {
      plans.update(plan.id, record);
    } else {
      record.id = uid();
      record.createdAt = now;
      plans.insert(record);
      plan.id = record.id;
    }
    persist();
    renderSaved();
    toast("여행 계획을 저장했습니다.", "success");
  } catch (e) {
    toast(e.message, "danger");
  }
});

$("#btn-new").addEventListener("click", () => {
  if (plan.stops.length && !confirm("작성 중인 계획을 비우고 새로 시작할까요? (저장한 계획은 저장 목록에 남아 있습니다)")) return;
  plan = emptyPlan();
  persist();
  fillMeta();
  renderStops();
});

const renderSaved = () => {
  const box = $("#saved-list");
  const user = currentUser();
  if (!user) {
    box.innerHTML = `<li class="list-group-item empty-state">로그인하면 저장한 계획을 볼 수 있습니다.<br><a class="btn btn-sm btn-primary mt-2" href="login.html?redirect=plan.html">로그인</a></li>`;
    return;
  }
  const mine = plans.filter((p) => p.userId === user.id).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  box.innerHTML = mine.length
    ? mine
        .map(
          (p) => `<li class="list-group-item d-flex align-items-center gap-2 py-3">
            <div class="flex-fill min-w-0">
              <p class="fw-bold mb-0 text-truncate">${escapeHtml(p.title)}</p>
              <p class="small text-body-secondary mb-0">${escapeHtml(p.startDate)} · 장소 ${p.stops.length}곳 · 수정 ${formatDate(p.updatedAt)}</p>
            </div>
            <button class="btn btn-sm btn-outline-primary" data-load="${p.id}" type="button">열기</button>
            <button class="btn btn-sm btn-outline-danger" data-del="${p.id}" type="button" aria-label="${escapeHtml(p.title)} 삭제">삭제</button>
          </li>`
        )
        .join("")
    : `<li class="list-group-item empty-state">저장한 계획이 없습니다.</li>`;
};

$("#saved-list").addEventListener("click", (e) => {
  const user = currentUser();
  const loadId = e.target.dataset.load;
  const delId = e.target.dataset.del;
  if (loadId) {
    const p = plans.get(loadId);
    if (!p || p.userId !== user?.id) return;
    const { userId, createdAt, updatedAt, ...rest } = p;
    plan = normalize(rest);
    persist();
    fillMeta();
    renderStops();
    bootstrap.Tab.getOrCreateInstance("#tab-plan").show();
  } else if (delId && confirm("이 계획을 삭제할까요?")) {
    if (plans.get(delId)?.userId !== user?.id) return;
    plans.remove(delId);
    if (plan.id === delId) plan.id = null;
    persist();
    renderSaved();
  }
});

/* =========================================================
   장소 찾기 탭
   ========================================================= */
const renderFind = () => {
  const ids = new Set(plan.stops.map((s) => s.spotId));
  $("#find-list").innerHTML = findResults.length
    ? findResults
        .map(
          (s, i) => `<li class="spot-item" style="grid-template-columns: 1fr auto">
            <span class="min-w-0">
              <span class="type-tag">${escapeHtml(contentTypeLabel(s.contentTypeId))}</span>
              <p class="spot-title text-truncate">${escapeHtml(s.title)}</p>
              <p class="spot-addr text-truncate">${escapeHtml(s.address)}</p>
            </span>
            <button class="btn btn-sm ${ids.has(s.id) ? "btn-outline-secondary" : "btn-primary"}" data-add="${i}" type="button" ${ids.has(s.id) ? "disabled" : ""}>
              ${ids.has(s.id) ? "담음" : "+ 담기"}</button>
          </li>`
        )
        .join("")
    : `<li class="empty-state">검색 결과가 없습니다.</li>`;
};

$("#find-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const { items } = await searchSpots({
    keyword: $("#find-keyword").value.trim(),
    sido: $("#find-sido").value,
    contentTypeId: $("#find-type").value,
    size: 20,
  });
  findResults = items;
  renderFind();
});

$("#find-list").addEventListener("click", (e) => {
  const idx = e.target.dataset.add;
  if (idx === undefined) return;
  addSpotToDraft(findResults[idx]);
  plan = { ...plan, stops: loadDraft().stops };
  renderFind();
  renderStops();
  toast(`'${findResults[idx].title}'을(를) 계획에 담았습니다.`);
});

/* =========================================================
   초기화
   ========================================================= */
const init = async () => {
  $("#find-type").innerHTML = CONTENT_TYPES.map((t) => `<option value="${t.id}">${t.label === "전체" ? "유형 전체" : t.label}</option>`).join("");
  $("#ev-connector").innerHTML = Object.entries(CONNECTORS)
    .map(([k, v]) => `<option value="${k}">${k ? v.label : "커넥터 전체"}</option>`)
    .join("");
  $("#ev-connector").value = prefs.get("connector");
  $("#ev-connector").addEventListener("change", (e) => {
    prefs.set("connector", e.target.value);
    renderChargers();
  });
  $("#ev-toggle").addEventListener("change", () => renderChargers());

  fillMeta();
  renderSaved();
  getSidoList().then((rows) => {
    $("#find-sido").innerHTML += rows.map((r) => `<option value="${escapeHtml(r.code)}">${escapeHtml(r.name)}</option>`).join("");
  });

  try {
    await loadKakaoMap();
    mapView = createMap($("#map"));
    evLayer = mapView.createLayer();
  } catch (e) {
    showMapNotice($("#map"), e.message);
  }
  renderStops();
  // 지도 탭 레이아웃이 바뀌면 다시 맞춘다 (모바일에서 탭 전환 시)
  document.querySelectorAll('[data-bs-toggle="tab"]').forEach((t) => t.addEventListener("shown.bs.tab", () => mapView?.relayout()));
};

init();
