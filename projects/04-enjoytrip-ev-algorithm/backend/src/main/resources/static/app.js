(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const fmtKm = (v) => v.toLocaleString("ko-KR", { maximumFractionDigits: 1 }) + "km";
  const fmtMin = (m) => { const h = Math.floor(m / 60), r = Math.round(m % 60); return h ? `${h}시간 ${r}분` : `${r}분`; };
  const ll = (p) => [p.lat, p.lng];
  const R = window.DEMO_RESULT;

  // ---------------------------------------------------------------- map
  const map = L.map("map", { preferCanvas: true });
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 18, attribution: "&copy; OpenStreetMap contributors" }).addTo(map);
  map.setView([35.0, 126.9], 8);
  const pinIcon = (text, cls) => L.divIcon({ className: "", html: `<div class="pin ${cls}">${esc(text)}</div>`, iconSize: [26, 26], iconAnchor: [13, 13] });
  const boltIcon = L.divIcon({ className: "", html: '<div class="bolt" aria-hidden="true">⚡</div>', iconSize: [24, 24], iconAnchor: [12, 12] });

  // 탭마다 지도 레이어를 따로 두고 탭을 바꿀 때 교체한다
  const layers = { route: L.layerGroup(), search: L.layerGroup(), recommend: L.layerGroup(), sort: L.layerGroup(), password: L.layerGroup(), results: L.layerGroup() };
  let currentTab = "route";
  let fitRoute = null;
  let base = R ? { lat: R.start.lat, lng: R.start.lng, name: R.start.name } : { lat: 35.1378, lng: 126.7932, name: "광주송정역" };
  const baseMarker = L.marker([base.lat, base.lng], { icon: pinIcon("◎", "start"), zIndexOffset: 1000 });

  // ---------------------------------------------------------------- API
  let serverOk = null;
  async function api(path, options) {
    const res = await fetch(path, options);
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(json.error || `요청 실패 (${res.status})`);
    // 백엔드는 관광지 공통 정보를 items[].spot 에 담아 보낸다. 화면에서는 한 단계로 펼쳐 쓴다.
    if (Array.isArray(json.items)) json.items = json.items.map((it) => (it && it.spot ? { ...it.spot, ...it, spot: undefined } : it));
    return json;
  }
  const metaPromise = api("/api/meta").then((m) => { serverOk = true; return m; }).catch(() => { serverOk = false; return null; });

  // ---------------------------------------------------------------- tabs
  const tabNames = ["route", "search", "recommend", "sort", "password", "results"];
  function showTab(name) {
    currentTab = name;
    tabNames.forEach((t) => {
      $("tab-" + t).setAttribute("aria-selected", t === name);
      $("panel-" + t).hidden = t !== name;
      map.removeLayer(layers[t]);
    });
    map.addLayer(layers[name]);
    if (name === "recommend" || name === "sort") baseMarker.addTo(map); else map.removeLayer(baseMarker);
    $("server-note").hidden = name === "route" || name === "results" || serverOk !== false;
    $("results-view").hidden = name !== "results";
    if (name === "results") renderResults();
    if (name === "sort" && serverOk) loadSort();
    if (name === "route" && fitRoute) fitRoute();
  }
  tabNames.forEach((t, i) => {
    const btn = $("tab-" + t);
    btn.addEventListener("click", () => showTab(t));
    btn.addEventListener("keydown", (e) => {
      if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
      const next = tabNames[(i + (e.key === "ArrowRight" ? 1 : tabNames.length - 1)) % tabNames.length];
      $("tab-" + next).focus();
      showTab(next);
    });
  });
  metaPromise.then(() => { $("server-note").hidden = currentTab === "route" || currentTab === "results" || serverOk !== false; });

  // 결과 목록 → 지도 마커
  function showSpots(layer, items, cls, label) {
    layer.clearLayers();
    items.forEach((s, i) => L.marker(ll(s), { icon: pinIcon(label ? label(s, i) : i + 1, cls) })
      .bindPopup(`<b>${esc(s.name)}</b><br>${esc(s.addr)}<br>${esc(s.category ?? "")} · 반경 3km 급속 충전소 ${s.fastChargersNearby ?? 0}곳`)
      .addTo(layer));
    if (items.length) map.fitBounds(L.latLngBounds(items.map(ll)).pad(0.2), { maxZoom: 12 });
  }
  function focusSpot(layer, s) {
    map.setView(ll(s), 13);
    layer.eachLayer((m) => { if (m.getLatLng && m.getLatLng().lat === s.lat && m.getLatLng().lng === s.lng) m.openPopup(); });
  }
  function clickable(li, fn) {
    li.tabIndex = 0;
    li.addEventListener("click", fn);
    li.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); fn(); } });
  }

  // ---------------------------------------------------------------- #1 #2 경로·충전 (정적 결과)
  (function route() {
    if (!R) { $("sub").textContent = "demo-result.js 가 없습니다. sh scripts/run.sh web 을 먼저 실행하세요."; return; }
    const layer = layers.route;
    $("sub").textContent = `${R.start.name} 출발 · 관광지 ${R.optimized.stops.length}곳 · 출발 배터리 ${R.vehicle.startSoc}% · ${R.vehicle.batteryKwh}kWh (1회 충전 ${Math.round(R.vehicle.rangeKm)}km)`;
    const saved = (R.asIs.km - R.optimized.km) / R.asIs.km * 100;
    $("stats").innerHTML = [[fmtKm(R.summary.km), "총 거리"], [R.summary.charges + "회", "충전"], [fmtMin(R.summary.totalMin), "주행+충전"], [R.summary.elapsedMs + "ms", "계산 시간"]]
      .map(([v, k]) => `<div><b>${v}</b><span>${k}</span></div>`).join("");
    $("stations-label").textContent = `급속 충전소 ${R.fastStations.length.toLocaleString("ko-KR")}곳 표시 (전체 ${R.summary.stationCount.toLocaleString("ko-KR")}곳 중 50kW 이상)`;

    const stationsLayer = L.layerGroup(R.fastStations.map(([lat, lng, kw]) =>
      L.circleMarker([lat, lng], { radius: 2.5, stroke: false, fillColor: "#3c6fd8", fillOpacity: 0.45 }).bindTooltip(`급속 ${kw}kW`)));
    const asIsPts = [R.start, ...R.asIs.stops, R.start];
    const asIsLayer = L.layerGroup([
      L.polyline(asIsPts.map(ll), { color: "#8a94a3", weight: 3, dashArray: "6 6" }),
      L.marker(ll(R.start), { icon: pinIcon("S", "start") }).bindPopup(esc(R.start.name)),
      ...R.asIs.stops.map((s, i) => L.marker(ll(s), { icon: pinIcon(i + 1, "asis") }).bindPopup(`${i + 1}. ${esc(s.name)}`)),
    ]);
    const optPts = [R.start, ...R.optimized.stops, R.start];
    const legPaths = R.legs.map((leg, i) => [optPts[i], ...leg.charges, optPts[i + 1]].map(ll));
    const legLines = legPaths.map((p) => L.polyline(p, { color: "#0b8a6f", weight: 4, opacity: 0.85 }));
    const optLayer = L.layerGroup([
      ...legLines,
      L.marker(ll(R.start), { icon: pinIcon("S", "start") }).bindPopup(esc(R.start.name)),
      ...R.optimized.stops.map((s, i) => L.marker(ll(s), { icon: pinIcon(i + 1, "opt") }).bindPopup(`${i + 1}. ${esc(s.name)}`)),
      ...R.legs.flatMap((leg) => leg.charges.map((c) => L.marker(ll(c), { icon: boltIcon, zIndexOffset: 500 })
        .bindPopup(`<b>⚡ ${esc(c.name)}</b><br>${c.kw}kW · ${c.arriveSoc.toFixed(1)}% → ${c.departSoc}% · ${Math.round(c.min)}분`))),
    ]);
    const reserve = R.vehicle.reserveSoc;
    $("legs").innerHTML = R.legs.map((leg, i) => {
      const soc = leg.arrivalSoc ?? 0;
      const charges = leg.charges.map((c) => `<div class="charge">⚡ <strong>${esc(c.name)}</strong> 에서 충전
        <div class="meta">${c.kw}kW · 배터리 ${c.arriveSoc.toFixed(1)}% → ${c.departSoc}% · ${Math.round(c.min)}분</div></div>`).join("");
      return `<li class="leg" tabindex="0" data-i="${i}">
        <div class="leg-head"><span><span class="no">${i + 1}</span>${esc(leg.from)} → ${esc(leg.to)}</span><span class="km">${fmtKm(leg.km)}</span></div>
        ${charges}
        <div class="soc">도착 배터리 <span class="bar"><i class="${soc < 20 ? "low" : ""}" style="width:${soc}%"></i><u style="left:${reserve}%"></u></span>
        <b>${leg.feasible ? soc.toFixed(1) + "%" : "도달 불가"}</b></div></li>`;
    }).join("");
    $("asis-list").innerHTML = R.asIs.stops.map((s) => `<li>${esc(s.name)}</li>`).join("");

    const allBounds = L.latLngBounds([...asIsPts, ...optPts].map(ll)).pad(0.08);
    fitRoute = () => map.fitBounds(allBounds);
    let activeLeg = -1;
    document.querySelectorAll(".leg").forEach((el) => clickable(el, () => {
      const i = Number(el.dataset.i);
      activeLeg = activeLeg === i ? -1 : i;
      document.querySelectorAll(".leg").forEach((x) => x.classList.toggle("active", Number(x.dataset.i) === activeLeg));
      legLines.forEach((line, j) => line.setStyle({ weight: j === activeLeg ? 7 : 4, opacity: activeLeg === -1 || j === activeLeg ? 0.9 : 0.3 }));
      map.fitBounds(activeLeg === -1 ? allBounds : L.latLngBounds(legPaths[activeLeg]).pad(0.4));
    }));
    const setMode = (opt) => {
      $("btn-opt").setAttribute("aria-pressed", opt);
      $("btn-asis").setAttribute("aria-pressed", !opt);
      $("opt-view").hidden = !opt;
      $("asis-view").hidden = opt;
      $("compare").innerHTML = opt
        ? `<span class="before">${fmtKm(R.asIs.km)}</span><span class="after">${fmtKm(R.optimized.km)}</span><span class="delta">−${saved.toFixed(1)}%</span>`
        : `<span class="after">${fmtKm(R.asIs.km)}</span><span>최적 순서보다 ${fmtKm(R.asIs.km - R.optimized.km)} 더 이동</span>`;
      $("method").textContent = !opt ? "사용자가 계획에 담은 순서 그대로 (출발지로 복귀)"
        : R.optimized.method === "HELD_KARP" ? "Held-Karp 최적해 (모든 순서 중 가장 짧음)" : "최근접 이웃 + 2-opt 근사해";
      layer.removeLayer(opt ? asIsLayer : optLayer);
      layer.addLayer(opt ? optLayer : asIsLayer);
      if (currentTab === "route") map.fitBounds(allBounds);
    };
    $("btn-opt").addEventListener("click", () => setMode(true));
    $("btn-asis").addEventListener("click", () => setMode(false));
    $("show-stations").addEventListener("change", (e) => e.target.checked ? layer.addLayer(stationsLayer) : layer.removeLayer(stationsLayer));
    layer.addTo(map);
    setMode(true);
  })();

  // ---------------------------------------------------------------- 자동완성 콤보박스 (검색 탭, 추천 탭 찜하기에서 공용)
  function autocomplete(input, list, onPick, onEnter) {
    let items = [];
    let active = -1;
    let timer = null;
    const close = () => { list.hidden = true; input.setAttribute("aria-expanded", "false"); input.removeAttribute("aria-activedescendant"); active = -1; };
    const render = () => {
      list.innerHTML = items.map((s, i) => `<li id="${list.id}-${i}" role="option" aria-selected="${i === active}">
        <span>${esc(s.name)}</span><span class="meta">${esc(s.subCategory ?? "")} · ⚡${s.fastChargersNearby}</span></li>`).join("");
      list.hidden = items.length === 0;
      input.setAttribute("aria-expanded", String(!list.hidden));
      if (active >= 0) input.setAttribute("aria-activedescendant", `${list.id}-${active}`);
      list.querySelectorAll("li").forEach((li, i) => li.addEventListener("mousedown", (e) => { e.preventDefault(); onPick(items[i]); close(); }));
    };
    input.addEventListener("input", () => {
      clearTimeout(timer);
      const q = input.value.trim();
      if (!q || !serverOk) { items = []; render(); return; }
      timer = setTimeout(async () => {
        try {
          const r = await api(`/api/attractions/autocomplete?limit=8&q=${encodeURIComponent(q)}`);
          if (input.value.trim() !== q) return;
          items = r.items; active = -1; render();
          if (input === $("search-input")) {
            $("search-hint").textContent = (r.correctedQuery ? `한/영 전환 오타를 고쳐 "${r.correctedQuery}"(으)로 찾았어요 · ` : "")
              + `Trie 자동완성 ${r.items.length}개 · ${r.elapsedUs.toFixed(1)}µs`;
          }
        } catch (e) { items = []; render(); }
      }, 120);
    });
    input.addEventListener("keydown", (e) => {
      if (e.isComposing) return; // 한글 조합 중의 Enter·방향키는 무시
      if (e.key === "ArrowDown" && items.length) { e.preventDefault(); active = (active + 1) % items.length; render(); }
      else if (e.key === "ArrowUp" && items.length) { e.preventDefault(); active = (active - 1 + items.length) % items.length; render(); }
      else if (e.key === "Escape") { if (!list.hidden) { e.preventDefault(); close(); } } // 목록이 열려 있으면 입력값은 지우지 않고 목록만 닫기
      else if (e.key === "Enter") {
        e.preventDefault();
        if (active >= 0) { onPick(items[active]); close(); }
        else if (onEnter) { close(); onEnter(input.value.trim()); }
      }
    });
    input.addEventListener("blur", () => setTimeout(close, 100));
  }

  // ---------------------------------------------------------------- #4 #5 검색
  function highlight(text, positions, len) {
    let out = "", last = 0;
    positions.forEach((p) => { if (p < last) return; out += esc(text.slice(last, p)) + "<mark>" + esc(text.slice(p, p + len)) + "</mark>"; last = p + len; });
    return out + esc(text.slice(last));
  }
  async function runSearch(q) {
    if (!q || !serverOk) return;
    const r = await api(`/api/attractions/search?limit=30&q=${encodeURIComponent(q)}`).catch((e) => ({ error: e.message, items: [] }));
    const hint = $("search-hint");
    const ms = r.elapsedMs != null ? ` · ${r.elapsedMs.toFixed(2)}ms` : "";
    if (r.error) hint.textContent = r.error;
    else if (r.mode === "keyboard") hint.textContent = `한/영 전환 오타를 고쳐 "${r.correctedQuery}"(으)로 찾았어요: ${r.items.length}곳${ms}`;
    else if (r.mode === "fuzzy") {
      // 제안 단어는 버튼으로 — 누르면 그 말로 다시 정확 검색
      hint.innerHTML = `"${esc(r.correctedQuery ?? q)}"와(과) 정확히 일치하는 곳이 없어 비슷한 곳을 보여 드려요 (오타 허용 검색${ms}).<br>`
        + `혹시 <button type="button" class="suggestion" id="did-you-mean">${esc(r.suggestion)}</button>을(를) 찾으셨나요?`;
      $("did-you-mean").addEventListener("click", () => { $("search-input").value = r.suggestion; runSearch(r.suggestion); });
    } else hint.textContent = r.items.length ? `KMP 검색 "${q}": ${r.items.length}곳${ms} (관광지 이름·주소 1,504곳)`
      : `"${q}"와(과) 비슷한 관광지도 찾지 못했어요.`;
    const ol = $("search-results");
    const typo = (s) => s.distance > 0 ? ` <span class="typo">오타 ${s.distance}</span>` : "";
    ol.innerHTML = r.items.map((s, i) => `<li><div class="title"><span class="rank">${i + 1}</span>${s.field === "name" ? highlight(s.name, s.positions, s.keywordLength) : esc(s.name)}${typo(s)}</div>
      <div class="meta">${s.field === "addr" ? highlight(s.addr, s.positions, s.keywordLength) : esc(s.addr)} · ${esc(s.subCategory ?? "")}</div></li>`).join("");
    showSpots(layers.search, r.items, "alt");
    ol.querySelectorAll("li").forEach((li, i) => clickable(li, () => focusSpot(layers.search, r.items[i])));
  }
  autocomplete($("search-input"), $("suggest-list"), (s) => {
    $("search-input").value = s.name;
    $("search-results").innerHTML = `<li><div class="title">${esc(s.name)}</div><div class="meta">${esc(s.addr)} · ${esc(s.subCategory ?? "")} · 반경 3km 급속 충전소 ${s.fastChargersNearby}곳</div></li>`;
    showSpots(layers.search, [s], "alt");
    map.setView(ll(s), 13);
  }, runSearch);

  // ---------------------------------------------------------------- #7 추천
  const prefer = new Set();
  const liked = new Map();
  metaPromise.then((meta) => {
    if (!meta) return;
    const top = meta.categories.filter((c) => c.level === 1).concat(meta.categories.filter((c) => c.level === 2).sort((a, b) => b.count - a.count).slice(0, 10));
    $("prefer-chips").innerHTML = top.map((c) => `<button type="button" data-code="${esc(c.code)}" aria-pressed="false">${esc(c.name)}</button>`).join("");
    $("prefer-chips").querySelectorAll("button").forEach((b) => b.addEventListener("click", () => {
      const on = !prefer.has(b.dataset.code);
      on ? prefer.add(b.dataset.code) : prefer.delete(b.dataset.code);
      b.setAttribute("aria-pressed", on);
    }));
    $("sort-category").innerHTML = '<option value="">전체</option>' + meta.categories
      .filter((c) => c.level === 1).map((c) => `<option value="${esc(c.code)}">${esc(c.name)} (${c.count})</option>`).join("");
  });
  const renderLiked = () => {
    $("liked-chips").innerHTML = [...liked.values()].map((s) => `<button type="button" data-id="${esc(s.id)}" aria-label="${esc(s.name)} 찜 해제">${esc(s.name)}</button>`).join("");
    $("liked-chips").querySelectorAll("button").forEach((b) => b.addEventListener("click", () => { liked.delete(b.dataset.id); renderLiked(); }));
  };
  autocomplete($("like-input"), $("like-suggest"), (s) => { liked.set(s.id, s); $("like-input").value = ""; renderLiked(); });
  ["w-taste", "w-distance", "w-charger"].forEach((id) => $(id).addEventListener("input", (e) => { e.target.nextElementSibling.value = e.target.value; }));
  map.on("click", (e) => {
    if (currentTab !== "recommend" && currentTab !== "sort") return;
    base = { lat: +e.latlng.lat.toFixed(5), lng: +e.latlng.lng.toFixed(5), name: `지도 선택 (${e.latlng.lat.toFixed(3)}, ${e.latlng.lng.toFixed(3)})` };
    baseMarker.setLatLng([base.lat, base.lng]);
    $("base-label").textContent = base.name;
    if (currentTab === "sort") loadSort();
  });
  $("recommend-btn").addEventListener("click", async () => {
    if (!serverOk) { $("server-note").hidden = false; return; }
    const w = (id) => Number($(id).value) / 10;
    if (w("w-taste") + w("w-distance") + w("w-charger") === 0) { $("recommend-results").innerHTML = '<li class="meta">가중치를 하나 이상 올려 주세요.</li>'; return; }
    const params = new URLSearchParams({ k: 10, lat: base.lat, lng: base.lng, wTaste: w("w-taste"), wDistance: w("w-distance"), wCharger: w("w-charger"),
      prefer: [...prefer].join(","), liked: [...liked.keys()].join(",") });
    $("recommend-btn").disabled = true;
    try {
      const r = await api(`/api/attractions/recommend?${params}`);
      const bar = (label, v) => `<div><span>${label} ${(v * 100).toFixed(0)}</span><div class="bar"><i style="width:${v * 100}%"></i></div></div>`;
      $("recommend-results").innerHTML = r.items.map((s, i) => `<li><div class="title"><span class="rank">${i + 1}</span>${esc(s.name)}
        <span class="meta">${(s.score * 100).toFixed(0)}점</span></div>
        <div class="meta">${esc(s.category ?? "")} › ${esc(s.subCategory ?? "")} · ${s.km != null ? fmtKm(s.km) + " · " : ""}급속 충전소 ${s.fastChargers}곳</div>
        <div class="scores">${bar("취향", s.taste)}${bar("가까움", s.distance)}${bar("충전", s.charger)}</div></li>`).join("")
        + `<li class="meta">Top-K 힙으로 1,504곳 중 상위 10곳 선택 · ${r.elapsedMs.toFixed(2)}ms</li>`;
      showSpots(layers.recommend, r.items, "opt");
      $("recommend-results").querySelectorAll("li").forEach((li, i) => { if (r.items[i]) clickable(li, () => focusSpot(layers.recommend, r.items[i])); });
    } catch (e) {
      $("recommend-results").innerHTML = `<li class="meta">${esc(e.message)}</li>`;
    } finally {
      $("recommend-btn").disabled = false;
    }
  });

  // ---------------------------------------------------------------- #6 정렬
  let sortBy = "distance";
  document.querySelectorAll("#panel-sort .seg button").forEach((b) => b.addEventListener("click", () => {
    sortBy = b.dataset.by;
    document.querySelectorAll("#panel-sort .seg button").forEach((x) => x.setAttribute("aria-pressed", x === b));
    loadSort();
  }));
  $("sort-category").addEventListener("change", loadSort);
  async function loadSort() {
    if (!serverOk) return;
    const params = new URLSearchParams({ by: sortBy, category: $("sort-category").value, lat: base.lat, lng: base.lng, limit: 30 });
    try {
      const r = await api(`/api/attractions?${params}`);
      $("sort-results").innerHTML = `<li class="meta">${r.total.toLocaleString("ko-KR")}곳을 TimSort 로 2번 정렬 (이름 → ${sortBy === "distance" ? "거리" : sortBy === "chargers" ? "충전소 수" : "이름"}) · ${r.elapsedMs.toFixed(2)}ms · 상위 30곳</li>`
        + r.items.map((s, i) => `<li><div class="title"><span class="rank">${i + 1}</span>${esc(s.name)}</div>
          <div class="meta">${fmtKm(s.km)} · 급속 충전소 ${s.fastChargersNearby}곳 · ${esc(s.subCategory ?? "")}</div></li>`).join("");
      showSpots(layers.sort, r.items, "alt");
      $("sort-results").querySelectorAll("li").forEach((li, i) => { if (i > 0) clickable(li, () => focusSpot(layers.sort, r.items[i - 1])); });
    } catch (e) {
      $("sort-results").innerHTML = `<li class="meta">${esc(e.message)}</li>`;
    }
  }

  // ---------------------------------------------------------------- #3 비밀번호
  let lastStored = null;
  async function hash() {
    const pw = $("pw-input").value;
    if (!pw) { $("pw-output").innerHTML = '<p class="bad">비밀번호를 입력하세요.</p>'; return; }
    if (!serverOk) { $("server-note").hidden = false; return; }
    ["pw-hash", "pw-again"].forEach((id) => { $(id).disabled = true; });
    $("pw-output").textContent = "해시 계산 중… (키 스트레칭으로 일부러 느립니다)";
    try {
      const r = await api("/api/password/hash", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ password: pw }) });
      const sameSaltNote = lastStored && lastStored.pw === pw ? `<p class="ok">같은 비밀번호인데 salt 가 달라 해시가 완전히 다릅니다.</p>` : "";
      lastStored = { pw, stored: r.stored };
      $("pw-output").innerHTML = `<p>DB 에 저장되는 값 (평문은 저장하지 않음) · ${r.elapsedMs.toFixed(0)}ms</p><code>${esc(r.stored)}</code>
        <dl><dt>알고리즘</dt><dd>${esc(r.algorithm)}</dd><dt>반복 횟수</dt><dd>${r.iterations.toLocaleString("ko-KR")}회</dd>
        <dt>salt</dt><dd>${esc(r.salt)}</dd><dt>hash</dt><dd>${esc(r.hash)}</dd></dl>${sameSaltNote}`;
      $("pw-verify-result").textContent = "";
    } catch (e) {
      $("pw-output").innerHTML = `<p class="bad">${esc(e.message)}</p>`;
    } finally {
      ["pw-hash", "pw-again"].forEach((id) => { $(id).disabled = false; });
    }
  }
  $("pw-hash").addEventListener("click", hash);
  $("pw-again").addEventListener("click", hash);
  $("pw-verify").addEventListener("click", async () => {
    if (!lastStored) { $("pw-verify-result").innerHTML = '<span class="bad">먼저 해시를 만드세요.</span>'; return; }
    const pw = $("pw-verify-input").value;
    if (!pw) { $("pw-verify-result").innerHTML = '<span class="bad">비밀번호를 입력하세요.</span>'; return; }
    $("pw-verify").disabled = true;
    try {
      const r = await api("/api/password/verify", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ password: pw, stored: lastStored.stored }) });
      $("pw-verify-result").innerHTML = r.match ? `<span class="ok">일치 — 로그인 성공</span> · ${r.elapsedMs.toFixed(0)}ms`
        : `<span class="bad">불일치 — 로그인 실패</span> · ${r.elapsedMs.toFixed(0)}ms`;
    } catch (e) {
      $("pw-verify-result").innerHTML = `<span class="bad">${esc(e.message)}</span>`;
    } finally {
      $("pw-verify").disabled = false;
    }
  });

  // ---------------------------------------------------------------- 시연·성능 측정 결과 차트
  let resultsRendered = false;
  function renderResults() {
    if (resultsRendered) return;
    resultsRendered = true;
    const view = $("results-view");
    if (!window.Charts || !window.BENCH) { view.textContent = "차트 데이터(bench-data.js, charts.js)를 불러오지 못했습니다."; return; }
    const charts = Charts.all(window.BENCH, R);
    // SVG 문자열은 charts.js 가 만든 것이고, 데이터 문자열은 그 안에서 모두 이스케이프된다
    view.innerHTML = '<h2>실데이터 시연 결과와 성능 측정</h2>' + charts.map((c) => `<figure id="chart-${c.id}">${c.svg}
      <details><summary>값 목록 보기</summary><table><tbody></tbody></table></details></figure>`).join("");
    charts.forEach((c) => {
      const fig = $("chart-" + c.id);
      const rows = [...fig.querySelectorAll("[data-tip]")].map((el) => `<tr><td>${esc(el.getAttribute("data-tip"))}</td></tr>`);
      fig.querySelector("tbody").innerHTML = rows.join("");
    });
    $("result-nav").innerHTML = charts.map((c) => {
      const title = $("chart-" + c.id).querySelector("svg").getAttribute("aria-label");
      return `<a href="#chart-${c.id}"><span class="group">${esc(c.group)}</span>${esc(title.replace(/^#\d\s*/, ""))}</a>`;
    }).join("");
    $("result-nav").querySelectorAll("a").forEach((a) => a.addEventListener("click", (e) => {
      e.preventDefault();
      document.querySelector(a.getAttribute("href")).scrollIntoView({ behavior: "smooth", block: "start" });
    }));
    const tipEl = $("chart-tip");
    view.addEventListener("mousemove", (e) => {
      const target = e.target.closest("[data-tip]");
      if (!target) { tipEl.hidden = true; return; }
      tipEl.textContent = target.getAttribute("data-tip");
      tipEl.hidden = false;
      const x = Math.min(e.clientX + 14, window.innerWidth - tipEl.offsetWidth - 8);
      tipEl.style.left = x + "px";
      tipEl.style.top = (e.clientY + 14) + "px";
    });
    view.addEventListener("mouseleave", () => { tipEl.hidden = true; });
  }

  showTab("route");
})();
