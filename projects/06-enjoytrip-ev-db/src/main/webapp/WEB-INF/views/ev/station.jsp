<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="ev" />
<!doctype html>
<html lang="ko">
<head>
<title><c:out value="${station.name}" /> 예약 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<%@ include file="../fragments/leaflet.jspf" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="page-wrap" style="max-width:1100px">
  <nav class="small mb-2"><a href="${root}/ev?lat=${station.latitude}&lng=${station.longitude}">← 충전소 지도</a></nav>
  <div class="row g-4">
    <div class="col-lg-8">
      <h1 class="page-title mb-1"><c:out value="${station.name}" /></h1>
      <p class="text-body-secondary mb-3">최대 ${station.maxKw}kW · 충전기 ${station.chargerCount}대 = 시간당 ${station.chargerCount}자리</p>

      <ul class="nav panel-tabs mb-3" aria-label="날짜 선택">
        <c:forEach var="d" items="${dates}">
          <li class="nav-item">
            <a class="nav-link ${d == date ? 'active' : ''}" href="${root}/ev?action=station&statId=${fn:escapeXml(station.statId)}&date=${d}">${d.monthValue}/${d.dayOfMonth}</a>
          </li>
        </c:forEach>
      </ul>

      <h2 class="h6 fw-bold">시간대 좌석 <span class="small fw-normal text-body-secondary">파랑 = 예약됨, 회색 = 빈 자리. 빈 자리가 있는 시간대를 누르면 예약됩니다.</span></h2>
      <form method="post" action="${root}/ev?action=reserve" id="reserve-form">
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <input type="hidden" name="statId" value="${fn:escapeXml(station.statId)}">
        <div class="slot-grid">
          <c:forEach var="s" items="${slots}">
            <c:set var="mine" value="false" />
            <c:forEach var="r" items="${s.reservations}">
              <c:if test="${not empty sessionScope.loginUser && r.memberId == sessionScope.loginUser.memberId}"><c:set var="mine" value="true" /></c:if>
            </c:forEach>
            <button type="submit" name="slot" value="${s.slotStart}" class="slot-cell ${s.full ? 'is-full' : ''} ${s.past ? 'is-past' : ''}"
              ${s.full || s.past || mine ? 'disabled' : ''} aria-label="${s.label} 남은 자리 ${s.remaining}">
              <span class="time">${s.label}</span>
              <span class="d-block small">${mine ? '내 예약' : (s.full ? '만석' : '남은 ' += s.remaining)}</span>
              <span class="seats" aria-hidden="true">
                <c:forEach var="r" items="${s.reservations}"><b class="${not empty sessionScope.loginUser && r.memberId == sessionScope.loginUser.memberId ? 'me' : 'on'}"></b></c:forEach>
                <c:forEach begin="1" end="${s.remaining}"><b></b></c:forEach>
              </span>
            </button>
          </c:forEach>
        </div>
      </form>
      <c:if test="${empty sessionScope.loginUser}">
        <p class="small text-body-secondary mt-2">예약하려면 <a href="${root}/member?action=login-form">로그인</a>하세요.</p>
      </c:if>
    </div>
    <aside class="col-lg-4">
      <div id="map" style="height:260px;border-radius:10px" class="mb-3"></div>
      <div class="border rounded p-3 small">
        <p class="fw-bold mb-1">동시에 눌러도 한 자리에 한 명</p>
        <p class="mb-0 text-body-secondary">예약은 DB 트랜잭션 안에서 해당 시간대 행을 잠그고(SELECT … FOR UPDATE) 남은 자리를 확인한 뒤 확정합니다.
          수천 명이 동시에 누르는 상황은 <a href="${root}/sim">예약 몰림 시뮬레이션</a>에서 볼 수 있습니다.</p>
      </div>
    </aside>
  </div>
</main>
<script type="application/json" id="station-data">${stationJson}</script>
<%@ include file="../fragments/footer.jsp" %>
<script>
  const st = ET.data("station-data");
  const map = ET.createMap("map", { lat: st.lat, lng: st.lng, zoom: 15 });
  L.marker([st.lat, st.lng]).addTo(map);
  document.getElementById("reserve-form").addEventListener("submit", (e) => {
    const label = e.submitter?.querySelector(".time")?.textContent;
    if (!confirm(`\${label} 충전 예약을 할까요?`)) e.preventDefault();
  });
</script>
</body>
</html>
