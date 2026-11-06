// 여행 계획 편집기 (F104)
// 장소 검색 → 담기 → 드래그로 동선 변경 → 방문 순서 최적화 / 충전 경로 계산(서버의 4회차 알고리즘) → 저장
(() => {
  const $ = (sel) => document.querySelector(sel);
  const esc = ET.escapeHtml;
  const MAX_STOPS = window.PLAN_MAX_STOPS || 30;
  const data = ET.data("plan-data");

  let keySeq = 0;
  const toStop = (s) => ({
    key: `s${++keySeq}`, id: s.id, title: s.title, lat: s.lat, lng: s.lng, type: s.type || "", addr: s.addr || "",
    visitDate: s.visitDate || "", cost: s.cost || 0, memo: s.memo || "",
  });

  const state = {
    planId: data.planId || 0,
    stops: data.stops.map(toStop),
    route: null, // 방문 순서 최적화 결과
    ev: null, // 충전 경로 계산 결과 (순서·배터리가 바뀌면 다시 계산해야 하므로 지운다)
    found: [], // 검색 결과
    dirty: false,
  };

  // ---------------------------------------------------------------- 기본 정보
  const title = $("#plan-title");
  const start = $("#plan-start");
  const end = $("#plan-end");
  const soc = $("#plan-soc");
  const back = $("#plan-return");
  const memo = $("#plan-memo");
  title.value = data.title;
  start.value = data.startDate;
  end.value = data.endDate;
  soc.value = data.startSoc || 80;
  memo.value = data.memo;

  // ---------------------------------------------------------------- 지도
  const map = ET.createMap("map");
  const findLayer = L.layerGroup().addTo(map);
  const stopLayer = L.layerGroup().addTo(map);
  const evLayer = L.layerGroup().addTo(map);

  const stopIcon = (n) =>
    L.divIcon({ className: "", html: `<div class="num-marker"><span>${n}</span></div>`, iconSize: [28, 28], iconAnchor: [14, 28], popupAnchor: [0, -26] });
  const evIcon = L.divIcon({ className: "", html: '<span class="ev-pin" aria-hidden="true">⚡</span>', iconSize: [26, 26], iconAnchor: [13, 13] });

  /** 서버 HaversineDistance 와 같은 근사: 대권거리 × 1.3 (도로 우회 계수). 화면 안내용 */
  const approxKm = (a, b) => {
    const rad = (d) => (d * Math.PI) / 180;
    const h = Math.sin(rad(b.lat - a.lat) / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(rad(b.lng - a.lng) / 2) ** 2;
    return 2 * 6371.0088 * Math.asin(Math.sqrt(h)) * 1.3;
  };
  const formatMin = (m) => (m >= 60 ? `${Math.floor(m / 60)}시간 ${Math.round(m % 60)}분` : `${Math.round(m)}분`);
  const won = (n) => Number(n || 0).toLocaleString("ko-KR");

  // ---------------------------------------------------------------- 렌더링
  const chargeLines = (leg) =>
    leg.chargeStops
      .map((c) => `<span class="charge-line d-block">⚡ ${esc(c.name)} 충전 · ${c.arriveSoc}% → ${c.departSoc}% · ${c.chargeMin}분 <span class="text-body-secondary">(${c.maxKw}kW)</span></span>`)
      .join("");

  /** i 번째 구간(stops[i] → 다음 지점) 안내 줄 */
  const legHtml = (i, from, to, isReturn) => {
    const leg = state.ev?.legs[i];
    let text = `${isReturn ? "↩ 출발지로" : "↓"} 약 ${approxKm(from, to).toFixed(1)}km`;
    if (leg && leg.feasible) {
      text = `${isReturn ? "↩ 출발지로" : "↓"} ${leg.km}km · 주행 ${formatMin(leg.driveMin)}${leg.chargeMin ? ` + 충전 ${formatMin(leg.chargeMin)}` : ""} · 도착 배터리 ${leg.arrivalSoc}%`;
    }
    const warn = leg && !leg.feasible ? '<span class="charge-line is-warn d-block">⚠ 충전소를 거쳐도 이 구간을 갈 수 없습니다</span>' : "";
    return `<li class="leg" aria-hidden="true">${text}${leg && leg.feasible ? chargeLines(leg) : ""}${warn}</li>`;
  };

  const stopHtml = (s, i) => `
    <li class="stop" data-key="${s.key}" draggable="true">
      <button type="button" class="drag-handle" aria-label="${esc(s.title)} 순서 이동 (Alt+위/아래)">⋮⋮</button>
      <span class="stop-no" aria-hidden="true">${i + 1}</span>
      <div class="min-w-0">
        <p class="stop-name text-truncate">${esc(s.title)}${i === 0 ? ' <span class="badge text-bg-light border fw-normal">출발</span>' : ""}</p>
        <p class="small text-body-secondary mb-0 text-truncate">${esc(s.type)}${s.addr ? ` · ${esc(s.addr)}` : ""}</p>
        <div class="stop-fields">
          <label class="visually-hidden" for="date-${s.key}">${esc(s.title)} 방문 날짜</label>
          <input id="date-${s.key}" type="date" class="form-control" data-field="visitDate" value="${esc(s.visitDate)}"
            min="${esc(start.value)}" max="${esc(end.value)}">
          <label class="visually-hidden" for="cost-${s.key}">${esc(s.title)} 경비(원)</label>
          <input id="cost-${s.key}" type="number" class="form-control" data-field="cost" min="0" step="1000" placeholder="경비(원)" value="${s.cost || ""}">
        </div>
        <label class="visually-hidden" for="memo-${s.key}">${esc(s.title)} 메모</label>
        <input id="memo-${s.key}" class="form-control form-control-sm mt-1" data-field="memo" maxlength="300" placeholder="메모 (예: 점심, 입장료)" value="${esc(s.memo)}">
      </div>
      <button type="button" class="btn btn-sm btn-link text-body-secondary" data-remove="${s.key}" aria-label="${esc(s.title)} 빼기">✕</button>
    </li>`;

  const renderStops = () => {
    const list = $("#stop-list");
    const stops = state.stops;
    if (stops.length === 0) {
      list.innerHTML = `<li class="empty-state">아직 담은 장소가 없습니다.<br>
        <button class="btn btn-sm btn-outline-primary mt-2" type="button" id="go-find">장소 찾으러 가기</button></li>`;
      $("#go-find").addEventListener("click", () => bootstrap.Tab.getOrCreateInstance("#tab-find").show());
    } else {
      let html = "";
      stops.forEach((s, i) => {
        html += stopHtml(s, i);
        if (i < stops.length - 1) html += legHtml(i, s, stops[i + 1], false);
      });
      if (back.checked && stops.length > 1) html += legHtml(stops.length - 1, stops[stops.length - 1], stops[0], true);
      list.innerHTML = html;
    }
    $("#stop-count").textContent = stops.length;
    renderSum();
  };

  const renderSum = () => {
    $("#sum-cost").textContent = won(state.stops.reduce((a, s) => a + (Number(s.cost) || 0), 0));
  };

  const renderMap = (fit) => {
    stopLayer.clearLayers();
    const pts = state.stops.map((s) => [s.lat, s.lng]);
    state.stops.forEach((s, i) => {
      L.marker([s.lat, s.lng], { icon: stopIcon(i + 1), title: s.title })
        .bindPopup(`<div class="map-iw"><strong>${i + 1}. ${esc(s.title)}</strong><br><span>${esc(s.addr)}</span></div>`)
        .addTo(stopLayer);
    });
    if (pts.length > 1) {
      const line = back.checked ? [...pts, pts[0]] : pts;
      L.polyline(line, { color: "#059669", weight: 4, opacity: 0.75 }).addTo(stopLayer);
    }
    if (fit && pts.length) map.fitBounds(L.latLngBounds(pts).pad(0.2), { maxZoom: 13 });
  };

  const renderEv = () => {
    evLayer.clearLayers();
    const box = $("#ev-result");
    const ev = state.ev;
    if (!ev) {
      box.innerHTML = "";
      return;
    }
    ev.legs.forEach((leg) =>
      leg.chargeStops.forEach((c) =>
        L.marker([c.lat, c.lng], { icon: evIcon, title: c.name })
          .bindPopup(`<div class="map-iw"><strong>⚡ ${esc(c.name)}</strong><br><span>${c.maxKw}kW · ${c.arriveSoc}% → ${c.departSoc}% · ${c.chargeMin}분</span></div>`)
          .addTo(evLayer)
      )
    );
    if (ev.feasible) {
      box.innerHTML = `<div class="ev-summary">⚡ 충전 ${ev.chargeStopCount}회 · 총 ${ev.totalKm}km · 약 ${formatMin(ev.totalMin)} (주행+충전)</div>`;
    } else {
      const bad = ev.legs.find((l) => !l.feasible);
      box.innerHTML = `<div class="ev-summary is-warn">⚠ ${esc(bad.from.name)} → ${esc(bad.to.name)} 구간은 충전소를 거쳐도 갈 수 없습니다.
        출발 배터리를 높이거나 중간 방문지를 추가해 보세요.${ev.skippedLegs ? ` (이후 ${ev.skippedLegs}개 구간은 계산하지 않음)` : ""}</div>`;
    }
  };

  const render = (fit = false) => {
    renderStops();
    renderMap(fit);
    renderEv();
  };

  /** 동선이 바뀌면 이전 계산 결과는 더 이상 맞지 않는다 */
  const changed = () => {
    state.ev = null;
    state.route = null;
    $("#route-result").innerHTML = "";
    state.dirty = true;
  };

  const showError = (msg) => {
    const box = $("#editor-msg");
    box.textContent = msg;
    box.classList.toggle("d-none", !msg);
  };

  // ---------------------------------------------------------------- 방문지 편집
  const addStop = (item) => {
    if (state.stops.some((s) => s.id === item.id)) return showError("이미 담은 장소입니다.");
    if (state.stops.length >= MAX_STOPS) return showError(`장소는 최대 ${MAX_STOPS}곳까지 담을 수 있습니다.`);
    showError("");
    state.stops.push(toStop(item));
    changed();
    render(true);
    renderFind();
  };

  const moveStop = (from, to) => {
    if (to < 0 || to >= state.stops.length || from === to) return;
    const [item] = state.stops.splice(from, 1);
    state.stops.splice(to, 0, item);
    changed();
    render();
  };

  const list = $("#stop-list");
  const indexOf = (key) => state.stops.findIndex((s) => s.key === key);

  list.addEventListener("click", (e) => {
    const key = e.target.closest("[data-remove]")?.dataset.remove;
    if (!key) return;
    state.stops = state.stops.filter((s) => s.key !== key);
    changed();
    render();
    renderFind();
  });

  // 방문 날짜·경비·메모: 다시 그리지 않고 값만 반영 (입력 중 포커스 유지)
  list.addEventListener("input", (e) => {
    const field = e.target.dataset.field;
    const key = e.target.closest(".stop")?.dataset.key;
    const stop = state.stops.find((s) => s.key === key);
    if (!field || !stop) return;
    stop[field] = field === "cost" ? Number(e.target.value) || 0 : e.target.value;
    state.dirty = true;
    if (field === "cost") renderSum();
  });

  // HTML5 드래그 앤 드롭 (3회차 plan.js 와 같은 방식)
  let dragKey = null;
  list.addEventListener("dragstart", (e) => {
    const li = e.target.closest(".stop");
    if (!li || e.target.matches("input")) return;
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
    if (e.key !== "ArrowUp" && e.key !== "ArrowDown") return;
    e.preventDefault();
    const key = e.target.closest(".stop").dataset.key;
    const idx = indexOf(key);
    moveStop(idx, idx + (e.key === "ArrowUp" ? -1 : 1));
    document.querySelector(`.stop[data-key="${key}"] .drag-handle`)?.focus();
  });

  // 기간이 바뀌면 방문 날짜 선택 범위도 바뀐다
  [start, end].forEach((el) => el.addEventListener("change", () => { state.dirty = true; renderStops(); }));
  soc.addEventListener("change", () => { state.ev = null; state.dirty = true; renderStops(); renderEv(); });
  back.addEventListener("change", () => { changed(); render(); });
  [title, memo].forEach((el) => el.addEventListener("input", () => { state.dirty = true; }));

  // ---------------------------------------------------------------- 장소 찾기
  const renderFind = () => {
    findLayer.clearLayers();
    const added = new Set(state.stops.map((s) => s.id));
    $("#find-list").innerHTML = state.found
      .map((a, i) => `
        <li>
          <div class="spot-item find-item">
            ${a.image ? `<img class="spot-thumb" src="${esc(a.image)}" alt="" loading="lazy">` : '<span class="spot-thumb">사진 없음</span>'}
            <button type="button" class="btn btn-link p-0 text-start text-decoration-none text-body min-w-0" data-focus="${i}">
              <span class="type-tag">${esc(a.type)}</span>
              <span class="spot-title d-block">${esc(a.title)}</span>
              <span class="spot-addr d-block text-truncate">${esc(a.addr)}</span>
            </button>
            ${added.has(a.id)
              ? '<span class="badge text-bg-light border">담김</span>'
              : `<button type="button" class="btn btn-sm btn-primary text-nowrap" data-add="${i}" aria-label="${esc(a.title)} 계획에 담기">담기</button>`}
          </div>
        </li>`)
      .join("");
    state.found.forEach((a) => {
      if (!a.lat || !a.lng || added.has(a.id)) return;
      L.circleMarker([a.lat, a.lng], { radius: 6, color: "#6b7280", weight: 2, fillOpacity: 0.5 })
        .bindTooltip(esc(a.title))
        .addTo(findLayer);
    });
  };

  let findQuery = null;
  let findPage = 1;
  let findRequest = 0;
  const findPlaces = async (page) => {
    const request = ++findRequest;
    const q = new URLSearchParams({ action: "search-json", ...findQuery, page: String(page) });
    const meta = $("#find-meta");
    const pages = $("#find-pages");
    pages.hidden = true;
    state.found = [];
    renderFind();
    meta.textContent = "검색 중…";
    try {
      const res = await ET.get(`/attraction?${q}`);
      if (request !== findRequest) return;
      state.found = res.items;
      findPage = res.page;
      meta.textContent = res.total ? `검색 결과 ${res.total.toLocaleString("ko-KR")}곳` : "검색 결과가 없습니다. 다른 이름으로 찾아보세요.";
      $("#find-page-label").textContent = `${res.page} / ${res.totalPages}`;
      $("#find-prev").disabled = res.page <= 1;
      $("#find-next").disabled = res.page >= res.totalPages;
      pages.hidden = res.totalPages <= 1;
      renderFind();
      const pts = res.items.filter((a) => a.lat && a.lng).map((a) => [a.lat, a.lng]);
      if (pts.length) map.fitBounds(L.latLngBounds(pts).pad(0.2), { maxZoom: 13 });
    } catch (err) {
      if (request !== findRequest) return;
      state.found = [];
      renderFind();
      meta.textContent = err.message;
    }
  };
  $("#find-form").addEventListener("submit", (e) => {
    e.preventDefault();
    findQuery = { keyword: $("#find-keyword").value.trim(), type: $("#find-type").value };
    findPlaces(1);
  });
  $("#find-prev").addEventListener("click", () => findPlaces(findPage - 1));
  $("#find-next").addEventListener("click", () => findPlaces(findPage + 1));

  $("#find-list").addEventListener("click", (e) => {
    const add = e.target.closest("[data-add]");
    if (add) return addStop(state.found[add.dataset.add]);
    const focus = e.target.closest("[data-focus]");
    if (focus) {
      const a = state.found[focus.dataset.focus];
      if (a.lat && a.lng) map.setView([a.lat, a.lng], 14);
    }
  });

  // ---------------------------------------------------------------- 서버 계산
  const ids = () => state.stops.map((s) => s.id).join(",");

  const busy = async (btn, label, job) => {
    const old = btn.textContent;
    btn.disabled = true;
    btn.textContent = label;
    showError("");
    try {
      await job();
    } catch (err) {
      showError(err.message);
    } finally {
      btn.disabled = false;
      btn.textContent = old;
    }
  };

  $("#btn-optimize").addEventListener("click", (e) => {
    if (state.stops.length < 3) return showError("순서를 최적화하려면 장소가 3곳 이상 필요합니다 (첫 장소는 출발지로 고정).");
    busy(e.currentTarget, "계산 중…", async () => {
      const res = await ET.post("/plan?action=optimize", { contentIds: ids(), returnToStart: back.checked });
      const byId = new Map(state.stops.map((s) => [s.id, s]));
      changed();
      state.stops = res.order.map((id) => byId.get(id));
      state.route = res;
      render();
      $("#route-result").innerHTML = `<div class="route-summary">✔ ${esc(res.methodLabel)} · 총 약 ${res.totalKm}km</div>`;
    });
  });

  $("#btn-ev").addEventListener("click", (e) => {
    if (state.stops.length < 2) return showError("충전 경로를 계산하려면 장소가 2곳 이상 필요합니다.");
    const startSoc = Number(soc.value);
    if (!(startSoc >= 10 && startSoc <= 100)) return showError("출발 배터리는 10~100% 사이로 입력하세요.");
    busy(e.currentTarget, "계산 중…", async () => {
      state.ev = await ET.post("/plan?action=ev-trip", { contentIds: ids(), startSoc, returnToStart: back.checked });
      renderStops();
      renderEv();
    });
  });

  // ---------------------------------------------------------------- 저장
  $("#btn-save").addEventListener("click", (e) => {
    if (!title.value.trim()) {
      showError("여행 제목을 입력하세요.");
      bootstrap.Tab.getOrCreateInstance("#tab-plan").show();
      return title.focus();
    }
    if (state.stops.length === 0) return showError("장소를 1곳 이상 담으세요.");
    const plan = {
      planId: state.planId,
      title: title.value.trim(),
      startDate: start.value,
      endDate: end.value,
      startSoc: Number(soc.value) || 80,
      memo: memo.value,
      stops: state.stops.map((s) => ({ contentId: s.id, visitDate: s.visitDate, cost: Number(s.cost) || 0, memo: s.memo })),
    };
    busy(e.currentTarget, "저장 중…", async () => {
      const res = await ET.post("/plan?action=save", { planJson: JSON.stringify(plan) });
      state.dirty = false;
      location.href = `${ET.ctx}/plan?action=detail&planId=${res.planId}`;
    });
  });

  window.addEventListener("beforeunload", (e) => {
    if (state.dirty) e.preventDefault();
  });

  render(true);
})();
