// 예약 몰림 시뮬레이션 화면: ① 인트로 장면 → 실행 → 250ms 마다 상태 폴링해 배터리 격자·좌석 배치도·로그를 그린다
(() => {
  const $ = (id) => document.getElementById(id);
  const fmt = (n) => Number(n ?? 0).toLocaleString("ko-KR");

  /* ---------- ① 인트로 ---------- */
  const intro = $("intro");
  const hideToday = $("intro-hide-today");
  const preference = window.ETSimIntro;
  const scenes = [...intro.querySelectorAll("[data-scene]")];
  const dots = intro.querySelector(".intro-dots");
  dots.innerHTML = scenes.map(() => "<i></i>").join("");
  let sceneIdx = -1;

  const showScene = (i) => {
    scenes.forEach((s, k) => {
      s.hidden = k !== i;
      s.classList.remove("is-on");
    });
    [...dots.children].forEach((d, k) => d.classList.toggle("is-on", k <= i));
    sceneIdx = i;
    requestAnimationFrame(() => requestAnimationFrame(() => scenes[i].classList.add("is-on")));
    if (scenes[i].querySelector("#intro-battery")) {
      const bat = $("intro-battery").querySelector("i");
      bat.style.width = "40%";
      setTimeout(() => (bat.style.width = "12%"), 60);
    }
  };
  const closeIntro = () => {
    preference.save(hideToday.checked);
    intro.hidden = true;
    document.removeEventListener("keydown", onKey);
  };
  const nextScene = () => (sceneIdx < scenes.length - 1 ? showScene(sceneIdx + 1) : null);
  const onKey = (e) => {
    if (intro.hidden || e.target.closest("input, label, button, a")) return;
    if (e.code === "Space" || e.code === "ArrowRight" || e.code === "Enter") { e.preventDefault(); nextScene(); }
    if (e.code === "Escape") closeIntro();
  };
  const openIntro = () => {
    hideToday.checked = preference.shouldSkip();
    intro.hidden = false;
    showScene(0);
    document.addEventListener("keydown", onKey);
  };
  intro.addEventListener("click", (e) => {
    if (e.target.closest("button, input, label, .intro-controls")) return;
    nextScene();
  });
  $("btn-skip").addEventListener("click", closeIntro);
  $("btn-replay").addEventListener("click", openIntro);
  $("btn-press").addEventListener("click", () => {
    closeIntro();
    start();
  });
  $("vehicles").addEventListener("change", (e) => ($("intro-cars").textContent = fmt(e.target.value)));
  hideToday.addEventListener("change", () => {
    if (!hideToday.checked) preference.save(false);
  });
  if (intro.dataset.autoOpen === "true" && !preference.shouldSkip()) openIntro();

  /* ---------- 실행 ---------- */
  let runId = 0;
  let since = 0;
  let soc = null;
  let polling = null;
  let myVehicle = 0;
  let lastLogStatus = "";

  const start = async () => {
    $("run-error").textContent = "";
    const form = new FormData($("run-form"));
    try {
      $("btn-start").disabled = true;
      const res = await ET.post("/sim?action=start", Object.fromEntries(form));
      follow(res.runId);
    } catch (e) {
      $("run-error").textContent = e.message;
      $("btn-start").disabled = false;
    }
  };
  $("run-form").addEventListener("submit", (e) => {
    e.preventDefault();
    start();
  });

  const follow = (id) => {
    runId = id;
    since = 0;
    soc = null;
    $("log").innerHTML = "";
    $("btn-start").disabled = true;
    clearInterval(polling);
    poll();
    polling = setInterval(poll, 250);
  };

  let inFlight = false;
  const poll = async () => {
    if (inFlight) return;
    inFlight = true;
    try {
      const s = await ET.get(`/sim?action=status&runId=${runId}&since=${since}&full=${soc ? "false" : "true"}`);
      if (s.soc) soc = s.soc;
      render(s);
      if (s.status !== "RUNNING") {
        clearInterval(polling);
        $("btn-start").disabled = false;
        refreshHistory();
      }
    } catch (e) {
      clearInterval(polling);
      $("btn-start").disabled = false;
      $("run-error").textContent = e.message;
    } finally {
      inFlight = false;
    }
  };

  const render = (s) => {
    const c = s.counts;
    $("k-published").textContent = fmt(c.published);
    $("k-queued").textContent = fmt(c.queued);
    $("k-confirmed").textContent = fmt(c.confirmed);
    $("k-retries").textContent = fmt(c.retries);
    $("k-rejected").textContent = fmt(c.rejected);
    $("k-elapsed").textContent = (s.elapsedMs / 1000).toFixed(1) + "s";
    $("k-tps").textContent = fmt(Math.round(c.processed / Math.max(0.1, s.elapsedMs / 1000)));
    const over = $("kpi-over");
    if (s.overbooked === null || s.overbooked === undefined) {
      $("k-over").textContent = "집계 중";
      over.className = "kpi";
    } else {
      $("k-over").textContent = fmt(s.overbooked) + "건";
      over.className = "kpi " + (s.overbooked > 0 ? "is-bad" : "is-good");
    }
    $("scenario").textContent = `시뮬레이션 #${s.runId} · ${s.mode === "LOCKED" ? "잠금 예약" : "잠금 없음"} · ${s.queue} · 차량 ${fmt(s.vehicles)}대 · ${s.status === "RUNNING" ? "진행 중" : s.status === "DONE" ? "완료" : "중단"}`;
    renderMyCar(s);
    drawGrid(s.states);
    renderSeats(s.seats);
    appendLogs(s.logs);
  };

  const renderMyCar = (s) => {
    const me = s.myCar;
    myVehicle = me.vehicleNo;
    $("my-ticket").textContent = me.ticketNo ? fmt(me.ticketNo) + "번" : "-";
    $("my-soc").textContent = me.soc;
    const el = $("my-status");
    const text = {
      i: ["wait", "줄 서는 중…"],
      q: ["wait", "1지망 충전소 차례를 기다리는 중"],
      w: ["wait", "만석! 다음 지망 충전소로 다시 줄 서는 중"],
      c: ["ok", `확정 — ${me.station ?? ""} ${me.chargerNo}번 충전기`],
      r: ["bad", "3지망까지 만석… 예약 실패"],
    }[me.state] ?? ["wait", ""];
    el.className = "status " + text[0];
    el.textContent = text[1];
  };

  /* ① 배터리 격자: 한 칸 = 차 한 대 */
  const canvas = $("grid");
  const ctx2d = canvas.getContext("2d");
  const socColor = (v) => (v < 15 ? "#ff4d5e" : v < 35 ? "#ffb347" : "#c9d36a");
  const drawGrid = (states) => {
    const n = states.length;
    $("grid-count").textContent = fmt(n);
    const w = canvas.clientWidth || 600;
    const cols = Math.ceil(Math.sqrt(n * (w / 200)));
    const cell = Math.max(3, Math.floor(w / cols));
    const rows = Math.ceil(n / cols);
    const dpr = window.devicePixelRatio || 1;
    canvas.width = cols * cell * dpr;
    canvas.height = rows * cell * dpr;
    canvas.style.height = rows * cell + "px";
    ctx2d.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx2d.clearRect(0, 0, cols * cell, rows * cell);
    const gap = cell > 6 ? 1 : 0;
    for (let i = 0; i < n; i++) {
      const st = states[i];
      const x = (i % cols) * cell;
      const y = Math.floor(i / cols) * cell;
      let color = "#1b2030";
      if (st === "c") color = "#41d98a";
      else if (st === "r") color = "#4a1d24";
      else if (soc) color = socColor(soc[i]);
      ctx2d.globalAlpha = st === "i" ? 0.25 : st === "w" ? 0.6 : 1;
      ctx2d.fillStyle = color;
      ctx2d.fillRect(x, y, cell - gap, cell - gap);
    }
    ctx2d.globalAlpha = 1;
    if (myVehicle) {
      const i = myVehicle - 1;
      ctx2d.strokeStyle = "#fff";
      ctx2d.lineWidth = 2;
      ctx2d.strokeRect((i % cols) * cell - 1, Math.floor(i / cols) * cell - 1, cell + 1, cell + 1);
    }
  };

  /* ③ 좌석 배치도 */
  const renderSeats = (rows) => {
    $("seats").innerHTML = rows
      .map((r) => {
        const seats = [];
        for (let k = 0; k < Math.max(r.capacity, r.booked.length); k++) {
          const v = r.booked[k];
          const cls = k >= r.capacity ? "seat over" : v === undefined ? "seat" : v === myVehicle ? "seat me" : "seat on";
          seats.push(`<span class="${cls}" title="${v === undefined ? "빈 자리" : "차량 " + v}"></span>`);
        }
        return `<div class="seat-row"><span class="name" title="${ET.escapeHtml(r.name)}">${ET.escapeHtml(r.name)} <small>${r.kw}kW</small></span><span class="seats">${seats.join("")}</span></div>`;
      })
      .join("");
  };

  const appendLogs = (logs) => {
    if (!logs.length) return;
    const box = $("log");
    const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 30;
    const frag = document.createDocumentFragment();
    logs.forEach((l) => {
      const div = document.createElement("div");
      div.className = l.kind;
      div.textContent = l.text;
      frag.appendChild(div);
      since = Math.max(since, l.seq);
    });
    box.appendChild(frag);
    while (box.childElementCount > 400) box.firstChild.remove();
    if (atBottom) box.scrollTop = box.scrollHeight;
  };

  const refreshHistory = async () => {
    try {
      const list = await ET.get("/sim?action=history");
      $("history").innerHTML = list
        .map((h) => `<tr><td>${h.runId}</td><td>${h.mode === "LOCKED" ? "잠금" : "잠금 없음"}</td><td>${ET.escapeHtml(h.queueType)}</td>
          <td>${fmt(h.vehicles)}</td><td>${fmt(h.confirmed)}</td>
          <td class="${h.overbooked > 0 ? "text-danger" : "text-success"} fw-bold">${h.status === "DONE" ? h.overbooked : "-"}</td>
          <td>${h.durationMs == null ? "-" : fmt(h.durationMs) + "ms"}</td></tr>`)
        .join("");
    } catch (e) {
      console.error(e);
    }
  };

  const boot = ET.data("sim-boot");
  if (boot.activeRunId) follow(boot.activeRunId);
})();
