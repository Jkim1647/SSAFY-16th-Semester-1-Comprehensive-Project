<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="ev" />
<!doctype html>
<html lang="ko">
<head>
<title>충전소 예약 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<%@ include file="../fragments/leaflet.jspf" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="app-shell">
  <section class="app-panel" aria-labelledby="panel-title">
    <div class="app-panel__head">
      <h1 id="panel-title" class="h5 fw-bold mb-1">충전소 예약</h1>
      <p class="small text-body-secondary mb-3">지도를 옮기면 그 주변 5km 충전소를 다시 찾습니다. 충전소를 골라 시간대 좌석을 예약하세요.</p>
      <form method="get" action="${root}/ev" class="vstack gap-2" role="search">
        <div class="input-group">
          <label class="visually-hidden" for="q">충전소 이름</label>
          <input id="q" name="q" class="form-control" type="search" value="<c:out value='${param.q}'/>" placeholder="충전소 이름 (예: 휴게소, 상무)" maxlength="30">
          <button class="btn btn-primary" type="submit">검색</button>
        </div>
        <div class="form-check form-switch small">
          <input class="form-check-input" type="checkbox" id="fast" ${param.minKw == '50' ? 'checked' : ''}>
          <label class="form-check-label" for="fast">급속(50kW 이상)만</label>
        </div>
      </form>
    </div>
    <div class="app-panel__body">
      <p class="result-meta mb-0" id="result-meta" aria-live="polite">${fn:length(stations)}곳</p>
      <ul class="spot-list" id="station-list"></ul>
    </div>
  </section>
  <section class="app-map" aria-label="지도">
    <div class="map-legend"><span class="ev-dot">⚡</span> 충전소 · 숫자 = 충전기 대수</div>
    <div id="map" class="map"></div>
  </section>
</main>
<script type="application/json" id="stations-data">${stationsJson}</script>
<script type="application/json" id="center-data">{"lat":${center.lat},"lng":${center.lng},"searched":${not empty param.q}}</script>
<%@ include file="../fragments/scripts.jspf" %>
<script>
  const center = ET.data("center-data");
  const map = ET.createMap("map", { lat: center.lat, lng: center.lng, zoom: 13 });
  const layer = L.layerGroup().addTo(map);
  const list = document.getElementById("station-list");
  const meta = document.getElementById("result-meta");
  const fast = document.getElementById("fast");

  const render = (stations) => {
    layer.clearLayers();
    meta.textContent = `\${stations.length}곳`;
    list.innerHTML = stations.length ? "" : '<li class="empty-state">이 근처에는 충전소가 없습니다.</li>';
    stations.forEach((s) => {
      const url = `\${ET.ctx}/ev?action=station&statId=\${encodeURIComponent(s.id)}`;
      const icon = L.divIcon({ className: "", html: `<button class="ev-marker" type="button" aria-label="\${ET.escapeHtml(s.name)}">\${s.chargers}</button>`, iconSize: [28, 28] });
      L.marker([s.lat, s.lng], { icon }).addTo(layer)
        .bindPopup(`<div class="map-iw"><strong>\${ET.escapeHtml(s.name)}</strong><br><span>\${s.kw}kW · 충전기 \${s.chargers}대</span><br><a href="\${url}">시간대 예약 →</a></div>`);
      const li = document.createElement("li");
      li.innerHTML = `<a class="spot-item text-decoration-none" href="\${url}" style="grid-template-columns:44px 1fr auto">
          <span class="ev-dot" style="width:36px;height:36px;line-height:36px;font-size:15px">⚡</span>
          <span class="min-w-0"><span class="spot-title d-block">\${ET.escapeHtml(s.name)}</span>
          <span class="spot-addr d-block">\${s.kw}kW · 충전기 \${s.chargers}대</span></span>
          <span class="small text-body-secondary">\${s.dist >= 0 ? s.dist + "km" : ""}</span></a>`;
      list.appendChild(li);
    });
  };

  render(ET.data("stations-data"));
  const first = ET.data("stations-data")[0];
  if (center.searched && first) map.setView([first.lat, first.lng], 12);

  let timer;
  const reload = () => {
    clearTimeout(timer);
    timer = setTimeout(async () => {
      const c = map.getCenter();
      try {
        render(await ET.get(`/ev?action=stations-json&lat=\${c.lat}&lng=\${c.lng}&radius=5&minKw=\${fast.checked ? 50 : 0}`));
      } catch (e) { meta.textContent = e.message; }
    }, 350);
  };
  if (!center.searched) map.on("moveend", reload);
  fast.addEventListener("change", reload);
</script>
</body>
</html>
