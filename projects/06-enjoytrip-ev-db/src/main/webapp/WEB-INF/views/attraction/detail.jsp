<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="attraction" />
<!doctype html>
<html lang="ko">
<head>
<title><c:out value="${spot.title}" /> · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<%@ include file="../fragments/leaflet.jspf" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="page-wrap" style="max-width:1100px">
  <nav aria-label="breadcrumb" class="small mb-2"><a href="javascript:history.back()">← 목록으로</a></nav>
  <div class="row g-4">
    <div class="col-lg-7">
      <span class="type-tag"><c:out value="${spot.contentTypeName}" /></span>
      <h1 class="page-title mb-3"><c:out value="${spot.title}" /></h1>
      <c:choose>
        <c:when test="${not empty spot.firstImage}"><img class="detail-photo mb-3" src="${fn:escapeXml(spot.firstImage)}" alt="<c:out value='${spot.title}'/> 사진"></c:when>
        <c:otherwise><div class="detail-photo mb-3 d-grid place-items-center text-body-secondary" style="place-items:center">사진 없음</div></c:otherwise>
      </c:choose>
      <dl class="kv mb-4">
        <dt>주소</dt><dd><c:out value="${spot.addr1}" /> <c:out value="${spot.addr2}" /></dd>
        <dt>지역</dt><dd><c:out value="${empty spot.signguName ? '-' : spot.signguName}" /></dd>
        <c:if test="${not empty spot.tel}"><dt>전화</dt><dd><c:out value="${spot.tel}" /></dd></c:if>
      </dl>

      <section class="mb-4" aria-labelledby="wx-title">
        <h2 class="h6 fw-bold" id="wx-title">7일 날씨 · 일출/일몰 <span class="small text-body-secondary fw-normal">(F111·F113, Open-Meteo)</span></h2>
        <div id="forecast" class="forecast"><div class="text-body-secondary" style="grid-column:1/-1">날씨를 불러오는 중…</div></div>
      </section>

      <div class="d-flex flex-wrap gap-2">
        <a class="btn btn-primary" href="${root}/plan?action=create-form&contentId=${spot.contentId}">여행 계획에 담기</a>
        <a class="btn btn-outline-primary" href="${root}/ev?lat=${spot.latitude}&lng=${spot.longitude}">근처 충전소 예약</a>
      </div>
    </div>
    <div class="col-lg-5">
      <div id="map" style="height:280px;border-radius:10px" class="mb-3"></div>
      <h2 class="h6 fw-bold">10km 안 충전소 <span class="small text-body-secondary fw-normal">(F114)</span></h2>
      <c:choose>
        <c:when test="${empty stations}"><p class="text-body-secondary small">근처에 등록된 충전소가 없습니다.</p></c:when>
        <c:otherwise>
          <ul class="list-group list-group-flush small">
            <c:forEach var="s" items="${stations}">
              <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                <span><a href="${root}/ev?action=station&statId=${fn:escapeXml(s.statId)}"><c:out value="${s.name}" /></a><br>
                  <span class="text-body-secondary">${s.maxKw}kW · 충전기 ${s.chargerCount}대</span></span>
                <span class="badge text-bg-light">${s.distanceKm}km</span>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </div>
  </div>
</main>
<script type="application/json" id="spot-data">${spotJson}</script>
<%@ include file="../fragments/footer.jsp" %>
<script>
  const spot = ET.data("spot-data");
  const map = ET.createMap("map", { lat: spot.lat, lng: spot.lng, zoom: 13 });
  L.marker([spot.lat, spot.lng]).addTo(map);

  // 날씨·일출/일몰: Open-Meteo 일별 예보 (키 불필요). 3회차 프론트 weather.js 와 같은 API
  const WMO = [[[0], "☀️", "맑음"], [[1, 2], "🌤️", "구름 조금"], [[3], "☁️", "흐림"], [[45, 48], "🌫️", "안개"],
    [[51, 53, 55, 56, 57], "🌦️", "이슬비"], [[61, 63, 65, 66, 67, 80, 81, 82], "🌧️", "비"],
    [[71, 73, 75, 77, 85, 86], "🌨️", "눈"], [[95, 96, 99], "⛈️", "뇌우"]];
  const icon = (code) => (WMO.find(([codes]) => codes.includes(code)) ?? [0, "🌡️", "정보 없음"]);
  const q = new URLSearchParams({ latitude: spot.lat, longitude: spot.lng, timezone: "Asia/Seoul", forecast_days: 7,
    daily: "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset" });
  fetch(`https://api.open-meteo.com/v1/forecast?\${q}`)
    .then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); })
    .then(({ daily: d }) => {
      document.getElementById("forecast").innerHTML = d.time.map((date, i) => {
        const [, ic, label] = icon(d.weather_code[i]);
        const day = "일월화수목금토"[new Date(`\${date}T00:00:00`).getDay()];
        return `<div title="\${label}"><div>\${date.slice(5).replace("-", "/")}(\${day})</div><div class="icon">\${ic}</div>
          <div class="max">\${Math.round(d.temperature_2m_max[i])}°</div><div class="min">\${Math.round(d.temperature_2m_min[i])}°</div>
          <div class="sun-line">↑\${d.sunrise[i].slice(11)}<br>↓\${d.sunset[i].slice(11)}</div></div>`;
      }).join("");
    })
    .catch(() => { document.getElementById("forecast").innerHTML = '<div class="text-body-secondary" style="grid-column:1/-1">날씨 정보를 불러오지 못했습니다.</div>'; });
</script>
</body>
</html>
