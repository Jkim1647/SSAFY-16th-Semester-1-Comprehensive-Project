// 의존성 없는 SVG 차트. 브라우저(window.Charts)와 Node(scripts/charts.mjs → README 이미지)에서 같은 코드를 쓴다.
// 각 마크에는 data-tip(웹 툴팁)과 <title>(정적 SVG 기본 툴팁)을 단다.
(function (root) {
  "use strict";

  const C = {
    surface: "#fcfcfb",
    ink: "#0b0b0b",
    ink2: "#52514e",
    muted: "#898781",
    grid: "#e1e0d9",
    axis: "#c3c2b7",
    series: ["#2a78d6", "#eb6834", "#1baf7a"], // 검증된 범주형 1~3번 (blue, orange, aqua)
    base: "#b4b2aa", // 비교 대상(기준선) 막대
    charge: "#eda100",
  };
  const FONT = "'Noto Sans KR', system-ui, -apple-system, 'Segoe UI', sans-serif";

  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const fmt = (v, d) => v == null ? "-" : v.toLocaleString("ko-KR", { maximumFractionDigits: d ?? (Math.abs(v) < 10 ? 2 : Math.abs(v) < 100 ? 1 : 0) });
  // 대략적인 글자 폭 (한글은 넓게) — 라벨이 막대 밖으로 나갈지 판단할 때 쓴다
  const textWidth = (s, size) => [...String(s)].reduce((w, ch) => w + (/[ㄱ-힝]/.test(ch) ? size : size * 0.58), 0);

  function frame(w, h, title, subtitle, body) {
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img" aria-label="${esc(title)}" font-family="${FONT}">`
      + `<rect width="${w}" height="${h}" rx="8" fill="${C.surface}"/>`
      + `<text x="20" y="28" font-size="15" font-weight="700" fill="${C.ink}">${esc(title)}</text>`
      + (subtitle ? `<text x="20" y="48" font-size="12" fill="${C.ink2}">${esc(subtitle)}</text>` : "")
      + body + `</svg>`;
  }
  const tip = (t) => ` data-tip="${esc(t)}"`;
  const titleEl = (t) => `<title>${esc(t)}</title>`;

  function legend(x, y, items) {
    let out = "";
    let cx = x;
    items.forEach((it) => {
      out += it.line
        ? `<line x1="${cx}" y1="${y - 4}" x2="${cx + 16}" y2="${y - 4}" stroke="${it.color}" stroke-width="2" stroke-linecap="round"/><circle cx="${cx + 8}" cy="${y - 4}" r="4" fill="${it.color}" stroke="${C.surface}" stroke-width="2"/>`
        : `<rect x="${cx}" y="${y - 10}" width="12" height="12" rx="3" fill="${it.color}"/>`;
      out += `<text x="${cx + 22}" y="${y}" font-size="12" fill="${C.ink2}">${esc(it.name)}</text>`;
      cx += 22 + textWidth(it.name, 12) + 18;
    });
    return out;
  }

  // 오른쪽 끝만 둥근 가로 막대 (기준선 쪽은 각지게)
  function hbar(x, y, w, h, color, extra) {
    const r = Math.min(4, w / 2, h / 2);
    if (w <= 0) return "";
    return `<path d="M${x},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h - r}Q${x + w},${y + h} ${x + w - r},${y + h}H${x}Z" fill="${color}"${extra || ""}/>`;
  }

  function niceTicks(max, count) {
    const raw = max / (count || 4);
    const mag = Math.pow(10, Math.floor(Math.log10(raw)));
    const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= raw);
    // 마지막 눈금이 최댓값 이상이 될 때까지 (막대가 축 밖으로 나가지 않게)
    const ticks = [];
    for (let v = 0; ; v += step) {
      ticks.push(+v.toFixed(10));
      if (v >= max) break;
    }
    return ticks;
  }

  /**
   * 가로 막대 (항목 비교). highlight 인 항목만 파란색, 나머지는 비교 기준이라 회색.
   * items: [{ label, value, highlight }]
   */
  function bars(o) {
    const w = o.width || 640;
    const labelW = o.labelWidth || 170;
    const top = o.subtitle ? 70 : 52;
    const rowH = 34;
    const barH = 18;
    const h = top + o.items.length * rowH + 34;
    const x0 = 20 + labelW;
    const plotW = w - x0 - 90;
    const ticks = niceTicks(Math.max(...o.items.map((i) => i.value)), 4);
    const max = ticks[ticks.length - 1];
    const sx = (v) => x0 + (v / max) * plotW;
    let body = "";
    ticks.forEach((t) => {
      body += `<line x1="${sx(t)}" y1="${top - 6}" x2="${sx(t)}" y2="${top + o.items.length * rowH}" stroke="${C.grid}" stroke-width="1"/>`
        + `<text x="${sx(t)}" y="${top + o.items.length * rowH + 16}" font-size="11" fill="${C.muted}" text-anchor="middle">${fmt(t)}</text>`;
    });
    o.items.forEach((it, i) => {
      const y = top + i * rowH + (rowH - barH) / 2;
      const bw = sx(it.value) - x0;
      const t = `${it.label}: ${fmt(it.value, o.digits)}${o.unit}${it.note ? " · " + it.note : ""}`;
      body += `<text x="${x0 - 10}" y="${y + barH / 2 + 4}" font-size="12" fill="${C.ink}" text-anchor="end">${esc(it.label)}</text>`;
      body += `<g${tip(t)}>${titleEl(t)}<rect x="${x0}" y="${y - 6}" width="${plotW + 80}" height="${barH + 12}" fill="transparent"/>`
        + hbar(x0, y, Math.max(bw, 2), barH, it.highlight ? C.series[0] : C.base) + `</g>`;
      body += `<text x="${x0 + Math.max(bw, 2) + 6}" y="${y + barH / 2 + 4}" font-size="12" font-weight="${it.highlight ? 700 : 400}" fill="${C.ink}">${fmt(it.value, o.digits)}${esc(o.unit)}</text>`;
    });
    body += `<line x1="${x0}" y1="${top - 6}" x2="${x0}" y2="${top + o.items.length * rowH}" stroke="${C.axis}" stroke-width="1"/>`;
    if (o.axisLabel) body += `<text x="${w - 20}" y="${top + o.items.length * rowH + 30}" font-size="11" fill="${C.muted}" text-anchor="end">${esc(o.axisLabel)}</text>`;
    return frame(w, h, o.title, o.subtitle, body);
  }

  /** 묶음 가로 막대 (범주 × 계열). series: [{ name, values }] */
  function groupedBars(o) {
    const w = o.width || 640;
    const labelW = o.labelWidth || 100;
    const top = (o.subtitle ? 70 : 52) + 22;
    const barH = 12;
    const gap = 2;
    const groupH = o.series.length * (barH + gap) + 16;
    const h = top + o.categories.length * groupH + 34;
    const x0 = 20 + labelW;
    const plotW = w - x0 - 96;
    const ticks = niceTicks(Math.max(...o.series.flatMap((s) => s.values)), 4);
    const max = ticks[ticks.length - 1];
    const sx = (v) => x0 + (v / max) * plotW;
    let body = legend(20, top - 14, o.series.map((s, i) => ({ name: s.name, color: C.series[i] })));
    const bottom = top + o.categories.length * groupH;
    ticks.forEach((t) => {
      body += `<line x1="${sx(t)}" y1="${top}" x2="${sx(t)}" y2="${bottom}" stroke="${C.grid}" stroke-width="1"/>`
        + `<text x="${sx(t)}" y="${bottom + 16}" font-size="11" fill="${C.muted}" text-anchor="middle">${fmt(t)}</text>`;
    });
    o.categories.forEach((cat, ci) => {
      const gy = top + ci * groupH + 8;
      body += `<text x="${x0 - 10}" y="${gy + (o.series.length * (barH + gap)) / 2 + 4}" font-size="12" fill="${C.ink}" text-anchor="end">${esc(cat)}</text>`;
      o.series.forEach((s, si) => {
        const y = gy + si * (barH + gap);
        const v = s.values[ci];
        const t = `${cat} · ${s.name}: ${fmt(v, 0)}${o.unit}`;
        body += `<g${tip(t)}>${titleEl(t)}<rect x="${x0}" y="${y - 1}" width="${plotW + 90}" height="${barH + 2}" fill="transparent"/>`
          + hbar(x0, y, Math.max(sx(v) - x0, 2), barH, C.series[si]) + `</g>`;
        if (o.labelSeries == null || o.labelSeries === si) {
          body += `<text x="${sx(v) + 6}" y="${y + barH - 2}" font-size="11" fill="${C.ink2}">${fmt(v, 0)}</text>`;
        }
      });
    });
    body += `<line x1="${x0}" y1="${top}" x2="${x0}" y2="${bottom}" stroke="${C.axis}" stroke-width="1"/>`;
    if (o.axisLabel) body += `<text x="${w - 20}" y="${bottom + 30}" font-size="11" fill="${C.muted}" text-anchor="end">${esc(o.axisLabel)}</text>`;
    return frame(w, h, o.title, o.subtitle, body);
  }

  /** 꺾은선. x: 숫자 배열, series: [{ name, values (null 은 건너뜀) }], yLog: 로그 축 */
  function lines(o) {
    const w = o.width || 640;
    const h = o.height || 300;
    const top = (o.subtitle ? 70 : 52) + (o.series.length > 1 ? 26 : 0) + 18;
    const left = 64;
    const right = 130;
    const bottom = h - 42;
    const xs = o.x;
    const sx = (v) => left + ((v - xs[0]) / (xs[xs.length - 1] - xs[0])) * (w - left - right);
    const all = o.series.flatMap((s) => s.values).filter((v) => v != null);
    let ticks;
    let sy;
    if (o.yLog) {
      const lo = Math.floor(Math.log10(Math.min(...all)));
      const hi = Math.ceil(Math.log10(Math.max(...all)));
      ticks = [];
      for (let e = lo; e <= hi; e++) ticks.push(Math.pow(10, e));
      sy = (v) => bottom - ((Math.log10(v) - lo) / (hi - lo)) * (bottom - top);
    } else {
      ticks = niceTicks(Math.max(...all), 4);
      const max = ticks[ticks.length - 1];
      sy = (v) => bottom - (v / max) * (bottom - top);
    }
    let body = o.series.length > 1 ? legend(20, top - 36, o.series.map((s, i) => ({ name: s.name, color: C.series[i], line: true }))) : "";
    ticks.forEach((t) => {
      body += `<line x1="${left}" y1="${sy(t)}" x2="${w - right + 10}" y2="${sy(t)}" stroke="${C.grid}" stroke-width="1"/>`
        + `<text x="${left - 8}" y="${sy(t) + 4}" font-size="11" fill="${C.muted}" text-anchor="end">${fmt(t)}</text>`;
    });
    xs.forEach((x) => {
      body += `<text x="${sx(x)}" y="${bottom + 18}" font-size="11" fill="${C.muted}" text-anchor="middle">${fmt(x)}</text>`;
    });
    body += `<line x1="${left}" y1="${bottom}" x2="${w - right + 10}" y2="${bottom}" stroke="${C.axis}" stroke-width="1"/>`;
    if (o.xLabel) body += `<text x="${w - right + 10}" y="${bottom + 34}" font-size="11" fill="${C.muted}" text-anchor="end">${esc(o.xLabel)}</text>`;
    if (o.yLabel) body += `<text x="${left - 8}" y="${top - 12}" font-size="11" fill="${C.muted}" text-anchor="start" dx="-40">${esc(o.yLabel)}</text>`;
    o.series.forEach((s, si) => {
      const color = C.series[si];
      const pts = xs.map((x, i) => [x, s.values[i]]).filter((p) => p[1] != null);
      body += `<polyline points="${pts.map((p) => `${sx(p[0])},${sy(p[1])}`).join(" ")}" fill="none" stroke="${color}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>`;
      pts.forEach((p) => {
        const t = `${s.name} · ${o.xName || "x"} ${fmt(p[0])}: ${fmt(p[1])}${o.unit}`;
        body += `<g${tip(t)}>${titleEl(t)}<circle cx="${sx(p[0])}" cy="${sy(p[1])}" r="12" fill="transparent"/>`
          + `<circle cx="${sx(p[0])}" cy="${sy(p[1])}" r="4" fill="${color}" stroke="${C.surface}" stroke-width="2"/></g>`;
      });
      const last = pts[pts.length - 1];
      body += `<text x="${sx(last[0]) + 10}" y="${sy(last[1]) + 4}" font-size="12" fill="${C.ink}">${fmt(last[1])}${esc(o.unit)}</text>`;
    });
    return frame(w, h, o.title, o.subtitle, body);
  }

  /** 여행 중 배터리 잔량(SOC) 변화. 시연 결과(demo-result.js)에서 계산한다. */
  function socProfile(R, o) {
    o = o || {};
    const w = o.width || 760;
    const h = 350;
    const top = 70;
    const left = 52;
    const right = 24;
    const bottom = h - 76;
    // 점: [누적 km, SOC, 종류, 이름]
    const pts = [[0, R.vehicle.startSoc, "start", R.start.name]];
    let km = 0;
    let soc = R.vehicle.startSoc;
    const kmPerPct = R.vehicle.rangeKm / 100;
    R.legs.forEach((leg) => {
      let used = 0;
      leg.charges.forEach((c) => {
        const d = (soc - c.arriveSoc) * kmPerPct;
        km += d;
        used += d;
        pts.push([km, c.arriveSoc, "arrive", c.name, c]);
        pts.push([km, c.departSoc, "charge", c.name, c]);
        soc = c.departSoc;
      });
      km += leg.km - used;
      soc = leg.arrivalSoc;
      pts.push([km, soc, "stop", leg.to]);
    });
    const maxKm = Math.ceil(km / 100) * 100;
    const sx = (v) => left + (v / maxKm) * (w - left - right);
    const sy = (v) => bottom - (v / 100) * (bottom - top);
    let body = "";
    [0, 20, 40, 60, 80, 100].forEach((t) => {
      body += `<line x1="${left}" y1="${sy(t)}" x2="${w - right}" y2="${sy(t)}" stroke="${C.grid}" stroke-width="1"/>`
        + `<text x="${left - 8}" y="${sy(t) + 4}" font-size="11" fill="${C.muted}" text-anchor="end">${t}%</text>`;
    });
    for (let t = 0; t <= maxKm; t += 100) body += `<text x="${sx(t)}" y="${bottom + 18}" font-size="11" fill="${C.muted}" text-anchor="middle">${t}</text>`;
    body += `<text x="${w - right}" y="${bottom + 56}" font-size="11" fill="${C.muted}" text-anchor="end">누적 이동 거리 (km) · 아래 숫자는 방문 순서 (S = 광주송정역)</text>`;
    // 최소 잔량 기준선
    body += `<line x1="${left}" y1="${sy(R.vehicle.reserveSoc)}" x2="${w - right}" y2="${sy(R.vehicle.reserveSoc)}" stroke="#d03b3b" stroke-width="1"/>`
      + `<text x="${w - right}" y="${sy(R.vehicle.reserveSoc) - 6}" font-size="11" fill="${C.ink2}" text-anchor="end">최소 잔량 ${R.vehicle.reserveSoc}%</text>`;
    // 잔량 영역 + 선
    const path = pts.map((p) => `${sx(p[0])},${sy(p[1])}`).join(" ");
    body += `<polygon points="${sx(0)},${sy(0)} ${path} ${sx(pts[pts.length - 1][0])},${sy(0)}" fill="${C.series[0]}" fill-opacity="0.1"/>`;
    body += `<polyline points="${path}" fill="none" stroke="${C.series[0]}" stroke-width="2" stroke-linejoin="round"/>`;
    let stopNo = 0;
    pts.forEach((p) => {
      const x = sx(p[0]);
      const y = sy(p[1]);
      if (p[2] === "charge") {
        const c = p[4];
        const t = `⚡ ${p[3]} (${c.kw}kW): ${c.arriveSoc.toFixed(1)}% → ${c.departSoc}%, ${Math.round(c.min)}분`;
        body += `<g${tip(t)}>${titleEl(t)}<rect x="${x - 12}" y="${y - 12}" width="24" height="${sy(c.arriveSoc) - y + 24}" fill="transparent"/>`
          + `<rect x="${x - 7}" y="${y - 7}" width="14" height="14" rx="3" fill="${C.charge}" stroke="${C.surface}" stroke-width="2"/></g>`
          + `<text x="${x}" y="${y - 12}" font-size="11" fill="${C.ink}" text-anchor="middle">⚡ ${esc(p[3])}</text>`;
      } else if (p[2] === "stop" || p[2] === "start") {
        const label = p[2] === "start" ? "S" : ++stopNo === R.legs.length ? "S" : String(stopNo);
        const t = `${p[2] === "start" ? "출발" : stopNo === R.legs.length ? "도착" : stopNo + "번 관광지"} ${p[3]} · ${fmt(p[0])}km · 배터리 ${p[1].toFixed(1)}%`;
        body += `<g${tip(t)}>${titleEl(t)}<circle cx="${x}" cy="${y}" r="10" fill="transparent"/><circle cx="${x}" cy="${y}" r="4" fill="${C.series[0]}" stroke="${C.surface}" stroke-width="2"/></g>`
          + `<text x="${x}" y="${bottom + 36}" font-size="11" font-weight="700" fill="${C.ink2}" text-anchor="middle">${label}</text>`;
      }
    });
    body += `<line x1="${left}" y1="${bottom}" x2="${w - right}" y2="${bottom}" stroke="${C.axis}" stroke-width="1"/>`;
    return frame(w, h, o.title || "여행 중 배터리 잔량 변화 (실데이터 시연)",
      o.subtitle || `${R.start.name} 출발 · 관광지 ${R.optimized.stops.length}곳 · 출발 ${R.vehicle.startSoc}% · 충전 ${R.summary.charges}회 · 총 ${fmt(R.summary.km)}km`, body);
  }

  /** 도로/직선 거리 비율 분포 (점 하나 = 지점 쌍). 같은 칸에 겹치는 점은 위로 쌓는다. */
  function ratioStrip(pairs, o) {
    o = o || {};
    const w = o.width || 640;
    const ratios = pairs.map((p) => ({ from: p[0], to: p[1], straight: p[2], road: p[3], r: p[3] / p[2] }));
    const lo = 1.0;
    const hi = Math.ceil(Math.max(...ratios.map((x) => x.r)) * 2) / 2;
    const left = 40;
    const right = 30;
    const sx = (v) => left + ((v - lo) / (hi - lo)) * (w - left - right);
    const bin = 0.04;
    const counts = {};
    ratios.sort((a, b) => a.r - b.r).forEach((x) => {
      const k = Math.round(x.r / bin);
      x.stack = counts[k] = (counts[k] || 0) + 1;
    });
    const maxStack = Math.max(...Object.values(counts));
    const top = 74;
    const step = 11;
    const base = top + maxStack * step + 10;
    const h = base + 56;
    let body = "";
    for (let t = lo; t <= hi + 1e-9; t += 0.5) {
      body += `<line x1="${sx(t)}" y1="${top - 10}" x2="${sx(t)}" y2="${base}" stroke="${C.grid}" stroke-width="1"/>`
        + `<text x="${sx(t)}" y="${base + 16}" font-size="11" fill="${C.muted}" text-anchor="middle">×${t.toFixed(1)}</text>`;
    }
    body += `<line x1="${sx(1.3)}" y1="${top - 14}" x2="${sx(1.3)}" y2="${base}" stroke="${C.ink2}" stroke-width="1.5"/>`
      + `<text x="${sx(1.3) + 6}" y="${top - 4}" font-size="11" fill="${C.ink2}">기존 근사 ×1.3</text>`;
    ratios.forEach((x) => {
      const cx = sx(Math.round(x.r / bin) * bin);
      const cy = base - 6 - (x.stack - 1) * step;
      const t = `${x.from} ↔ ${x.to}: 직선 ${fmt(x.straight)}km, 도로 ${fmt(x.road)}km (×${x.r.toFixed(2)})`;
      body += `<g${tip(t)}>${titleEl(t)}<circle cx="${cx}" cy="${cy}" r="8" fill="transparent"/><circle cx="${cx}" cy="${cy}" r="4.5" fill="${C.series[0]}" stroke="${C.surface}" stroke-width="2"/></g>`;
    });
    const top3 = ratios.slice(-1)[0];
    body += `<text x="${sx(Math.round(top3.r / bin) * bin)}" y="${base - 6 - top3.stack * step - 4}" font-size="11" fill="${C.ink}" text-anchor="end">${esc(top3.from)} ↔ ${esc(top3.to)} ×${top3.r.toFixed(2)}</text>`;
    body += `<line x1="${left}" y1="${base}" x2="${w - right}" y2="${base}" stroke="${C.axis}" stroke-width="1"/>`
      + `<text x="${w - right}" y="${base + 34}" font-size="11" fill="${C.muted}" text-anchor="end">도로 거리 ÷ 직선 거리 (점 하나 = 지점 2곳)</text>`;
    const sorted = ratios.map((x) => x.r).sort((a, b) => a - b);
    return frame(w, h, o.title || "#8 실제 도로 거리는 직선의 몇 배인가",
      o.subtitle || `시연 관광지 10곳 + 출발지, ${ratios.length}쌍 · 중앙값 ×${sorted[Math.floor(sorted.length / 2)].toFixed(2)} · 최대 ×${sorted[sorted.length - 1].toFixed(2)}`, body);
  }

  /** README·웹에서 함께 쓰는 차트 목록 */
  function all(B, R) {
    const charts = [];
    if (R) {
      charts.push({ id: "demo-soc", group: "시연", svg: socProfile(R) });
      charts.push({ id: "demo-distance", group: "시연", svg: bars({
        title: "#1 방문 순서 최적화로 줄어든 이동 거리 (실데이터 시연)", subtitle: "관광지 10곳, 출발지 복귀 · 직선거리 × 1.3 기준",
        unit: "km", digits: 1, labelWidth: 150, axisLabel: "총 이동 거리 (km)",
        items: [{ label: "담은 순서 그대로", value: R.asIs.km }, { label: "Held-Karp 최적 순서", value: R.optimized.km, highlight: true, note: `${((R.asIs.km - R.optimized.km) / R.asIs.km * 100).toFixed(1)}% 감소` }],
      }) });
    }
    charts.push({ id: "tsp-exact", group: "#1", svg: lines({
      title: "#1 정확 해법 시간: 완전탐색 O(n!) vs Held-Karp O(n²·2ⁿ)", subtitle: "로그 축 · 완전탐색은 n=12 부터 측정 불가(12! ≈ 4.8억)",
      x: B.tspExact.n, xName: "n", xLabel: "경유지 수 n", yLabel: "ms (로그)", unit: "ms", yLog: true,
      series: [{ name: "완전탐색", values: B.tspExact.bruteMs }, { name: "Held-Karp", values: B.tspExact.heldKarpMs }],
    }) });
    charts.push({ id: "tsp-heuristic", group: "#1", svg: lines({
      title: "#1 근사 해법이 최적해보다 더 도는 거리", subtitle: "크기별 무작위 50회 평균 · 2-opt 로 교차 구간을 풀면 1% 안팎",
      x: B.tspHeuristic.n, xName: "n", xLabel: "경유지 수 n", yLabel: "최적 대비 초과 %", unit: "%",
      series: [{ name: "최근접 이웃만", values: B.tspHeuristic.nnGapPct }, { name: "최근접 이웃 + 2-opt", values: B.tspHeuristic.twoOptGapPct }],
    }) });
    charts.push({ id: "ev-planner", group: "#2", svg: bars({
      title: "#2 충전소 경유 경로 계산 시간 (중앙값)", subtitle: "실제 충전소 8,072곳 · 기본 방식은 비교한 20쌍 모두 최적해와 같은 결과",
      unit: "ms", labelWidth: 170, axisLabel: "ms",
      items: B.evPlanner.map((e, i) => ({ label: e.label, value: e.medianMs, highlight: i === 0, note: `후보 ${fmt(e.candidates)}곳, 최댓값 ${fmt(e.maxMs)}ms` })),
    }) });
    charts.push({ id: "password", group: "#3", svg: bars({
      title: "#3 키 스트레칭: 반복 횟수와 해시 1회 시간", subtitle: "반복 횟수에 비례해 느려짐 · 공격자도 비밀번호 하나마다 같은 비용",
      unit: "ms", labelWidth: 110, axisLabel: "해시 1회 (ms)",
      items: B.password.iterations.map((n, i) => ({ label: `${fmt(n)}회`, value: B.password.ms[i], highlight: n === 600000, note: `1초에 약 ${fmt(1000 / B.password.ms[i], 0)}개 시도 가능` })),
    }) });
    charts.push({ id: "autocomplete", group: "#4", svg: bars({
      title: "#4 자동완성 검색어 1개 처리 시간", subtitle: "관광지 1,504곳 · 무작위 검색어 2,000개(초성 25%) 평균",
      unit: "µs", labelWidth: 150, axisLabel: "µs",
      items: B.autocomplete.map((e, i) => ({ label: e.label, value: e.us, highlight: i === 0 })),
    }) });
    charts.push({ id: "kmp", group: "#5", svg: bars({
      title: "#5 최악 입력에서의 문자열 검색 시간", subtitle: "본문 a×1,000,000 에서 패턴 a×1,000+b 찾기",
      unit: "ms", labelWidth: 150, axisLabel: "ms",
      items: B.kmp.map((e, i) => ({ label: e.label, value: e.ms, highlight: i === 0 })),
    }) });
    charts.push({ id: "sort", group: "#6", svg: groupedBars({
      title: "#6 정렬 비교 횟수 (Integer 100,000개)", subtitle: "이미 정렬된 구간이 많을수록 TimSort 가 유리 · 중복이 많으면 galloping 없는 직접 구현이 불리",
      unit: "회", labelWidth: 96, axisLabel: "비교 횟수",
      categories: B.sort.inputs, series: B.sort.series.map((s) => ({ name: s.name, values: s.comparisons })),
    }) });
    charts.push({ id: "grid", group: "#7", svg: bars({
      title: "#7 관광지별 반경 3km 급속 충전소 수 계산", subtitle: "관광지 1,504곳 × 급속 충전소 2,119곳",
      unit: "ms", labelWidth: 110, axisLabel: "ms",
      items: B.grid.map((e, i) => ({ label: e.label, value: e.ms, highlight: i === 0 })),
    }) });
    charts.push({ id: "road", group: "#8", svg: ratioStrip(B.road.pairs) });
    if (B.fuzzy) charts.push({ id: "fuzzy", group: "#9", svg: bars({
      title: "#9 오타가 1개 있는 검색어로 원래 관광지를 찾은 비율", subtitle: `실제 관광지 이름 일부(3~6글자)에 자모 오타 1개를 넣은 검색어 ${fmt(B.fuzzy.queries)}개`,
      unit: "%", digits: 1, labelWidth: 150, axisLabel: "찾은 비율 (%)",
      items: B.fuzzy.found.map((e, i) => ({ label: e.label, value: e.pct, highlight: i === 1 })),
    }) });
    return charts;
  }

  const api = { bars, groupedBars, lines, socProfile, ratioStrip, all, colors: C };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.Charts = api;
})(typeof window !== "undefined" ? window : this);
