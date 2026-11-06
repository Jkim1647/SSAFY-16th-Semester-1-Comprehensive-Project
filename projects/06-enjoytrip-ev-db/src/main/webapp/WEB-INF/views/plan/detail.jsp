<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="plan" />
<!doctype html>
<html lang="ko">
<head>
<title><c:out value="${plan.title}" /> · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<%@ include file="../fragments/leaflet.jspf" %>
<link rel="stylesheet" href="${root}/assets/css/plan.css">
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="page-wrap" style="max-width:1100px">
  <nav aria-label="breadcrumb" class="small mb-2"><a href="${root}/plan">← 내 여행 계획</a></nav>
  <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
    <div class="min-w-0">
      <h1 class="page-title mb-1"><c:out value="${plan.title}" /></h1>
      <p class="text-body-secondary small mb-0">
        <c:choose>
          <c:when test="${empty plan.startDate}">여행 날짜 미정</c:when>
          <c:otherwise>${plan.startDate}<c:if test="${not empty plan.endDate and plan.endDate != plan.startDate}"> ~ ${plan.endDate}</c:if>
            <c:if test="${not empty plan.days}"> (${plan.days}일)</c:if></c:otherwise>
        </c:choose>
        · 관광지 ${plan.stopCount}곳 · 출발 배터리 ${plan.startSoc}% · 수정 ${plan.updatedLabel}
      </p>
    </div>
    <div class="d-flex gap-2">
      <a class="btn btn-primary" href="${root}/plan?action=edit-form&planId=${plan.planId}">수정</a>
      <form method="post" action="${root}/plan?action=delete" onsubmit="return confirm('이 여행 계획을 삭제할까요? 되돌릴 수 없습니다.')">
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <input type="hidden" name="planId" value="${plan.planId}">
        <button class="btn btn-outline-danger" type="submit">삭제</button>
      </form>
    </div>
  </div>

  <c:if test="${not empty plan.memo}">
    <section class="plan-memo mb-4" aria-label="상세 계획">
      <c:out value="${plan.memo}" />
    </section>
  </c:if>

  <div class="row g-4">
    <div class="col-lg-6 order-lg-2">
      <div class="position-relative mb-2">
        <div class="map-legend">
          <span class="legend-stop" aria-hidden="true">1</span> 방문지
          <span class="ev-dot ms-2" aria-hidden="true">⚡</span> 충전소
        </div>
        <div id="map" class="plan-detail-map"></div>
      </div>

      <section class="mb-4" aria-labelledby="ev-title">
        <h2 class="h6 fw-bold mt-3" id="ev-title">⚡ 전기차 충전 계획 <span class="small text-body-secondary fw-normal">(저장한 순서, 중형 전기 SUV 77.4kWh 기준)</span></h2>
        <c:choose>
          <c:when test="${not empty tripError}"><p class="small text-danger"><c:out value="${tripError}" /></p></c:when>
          <c:when test="${empty trip}"><p class="small text-body-secondary">관광지가 2곳 이상이면 구간별 충전 계획을 계산합니다.</p></c:when>
          <c:when test="${trip.feasible}">
            <p class="ev-summary mb-2">충전 ${trip.chargeStopCount}회 · 총 ${trip.totalKm}km · 약 ${trip.totalTimeLabel} (주행+충전)</p>
          </c:when>
          <c:otherwise>
            <p class="ev-summary is-warn mb-2">⚠ 출발 배터리 ${plan.startSoc}%로는 충전소를 거쳐도 갈 수 없는 구간이 있습니다. 배터리를 높이거나 중간 방문지를 추가해 보세요.</p>
          </c:otherwise>
        </c:choose>
        <c:if test="${not empty trip}">
          <ol class="trip-legs small">
            <c:forEach var="leg" items="${trip.legs}">
              <li>
                <strong><c:out value="${leg.from.name}" /></strong> → <strong><c:out value="${leg.to.name}" /></strong>
                <c:choose>
                  <c:when test="${leg.feasible}">
                    <span class="text-body-secondary">· ${leg.km}km · 주행 ${leg.driveMin}분<c:if test="${leg.chargeMin > 0}"> + 충전 ${leg.chargeMin}분</c:if> · 도착 ${leg.arrivalSoc}%</span>
                    <c:forEach var="c" items="${leg.chargeStops}">
                      <span class="charge-line d-block">⚡ <c:out value="${c.name}" /> · ${c.maxKw}kW · ${c.arriveSoc}% → ${c.departSoc}% · ${c.chargeMin}분</span>
                    </c:forEach>
                  </c:when>
                  <c:otherwise><span class="charge-line is-warn d-block">갈 수 없는 구간</span></c:otherwise>
                </c:choose>
              </li>
            </c:forEach>
          </ol>
          <c:if test="${trip.skippedLegs > 0}"><p class="small text-body-secondary">이후 ${trip.skippedLegs}개 구간은 계산하지 않았습니다.</p></c:if>
        </c:if>
      </section>
    </div>

    <div class="col-lg-6 order-lg-1">
      <section class="mb-4" aria-labelledby="stops-title">
        <h2 class="h6 fw-bold" id="stops-title">여행 동선</h2>
        <ol class="stop-list px-0">
          <c:forEach var="s" items="${plan.stops}" varStatus="st">
            <li class="stop detail-stop">
              <span class="stop-no" aria-hidden="true">${st.count}</span>
              <div class="min-w-0">
                <p class="stop-name"><a class="text-body" href="${root}/attraction?action=detail&contentId=${s.contentId}"><c:out value="${s.title}" /></a></p>
                <p class="small text-body-secondary mb-0 text-truncate"><c:out value="${s.contentTypeName}" /> · <c:out value="${s.addr1}" /></p>
                <c:if test="${not empty s.memo}"><p class="small mb-0 mt-1"><c:out value="${s.memo}" /></p></c:if>
              </div>
              <div class="text-end small text-nowrap">
                <div>${empty s.visitDate ? '날짜 미정' : s.visitDate}</div>
                <div class="fw-bold"><fmt:formatNumber value="${s.cost}" />원</div>
              </div>
            </li>
          </c:forEach>
        </ol>
      </section>

      <section class="mb-4" aria-labelledby="cost-title">
        <h2 class="h6 fw-bold" id="cost-title">일자별 예상 경비</h2>
        <table class="table table-sm small expense-table">
          <caption class="visually-hidden">일자별 예상 경비</caption>
          <thead><tr><th scope="col">날짜</th><th scope="col" class="text-end">경비</th></tr></thead>
          <tbody>
            <c:forEach var="e" items="${plan.dailyCosts}">
              <tr><td>${e.key}</td><td class="text-end"><fmt:formatNumber value="${e.value}" />원</td></tr>
            </c:forEach>
          </tbody>
          <tfoot><tr class="fw-bold"><td>합계</td><td class="text-end"><fmt:formatNumber value="${plan.totalCost}" />원</td></tr></tfoot>
        </table>
        <p class="form-hint">충전 요금은 포함하지 않은 금액입니다.</p>
      </section>
    </div>
  </div>
</main>
<script type="application/json" id="plan-data">${planJson}</script>
<script type="application/json" id="trip-data">${tripJson}</script>
<%@ include file="../fragments/footer.jsp" %>
<script>
  (() => {
    const plan = ET.data("plan-data");
    const trip = ET.data("trip-data");
    const map = ET.createMap("map");
    const pts = plan.stops.filter((s) => s.lat && s.lng).map((s) => [s.lat, s.lng]);
    plan.stops.forEach((s, i) => {
      const icon = L.divIcon({ className: "", html: `<div class="num-marker"><span>\${i + 1}</span></div>`, iconSize: [28, 28], iconAnchor: [14, 28], popupAnchor: [0, -26] });
      L.marker([s.lat, s.lng], { icon, title: s.title })
        .bindPopup(`<div class="map-iw"><strong>\${i + 1}. \${ET.escapeHtml(s.title)}</strong><br><span>\${ET.escapeHtml(s.addr)}</span></div>`)
        .addTo(map);
    });
    if (pts.length > 1) L.polyline(pts, { color: "#059669", weight: 4, opacity: 0.75 }).addTo(map);
    const evIcon = L.divIcon({ className: "", html: '<span class="ev-pin" aria-hidden="true">⚡</span>', iconSize: [26, 26], iconAnchor: [13, 13] });
    (trip?.legs ?? []).forEach((leg) => leg.chargeStops.forEach((c) => {
      pts.push([c.lat, c.lng]);
      L.marker([c.lat, c.lng], { icon: evIcon, title: c.name })
        .bindPopup(`<div class="map-iw"><strong>⚡ \${ET.escapeHtml(c.name)}</strong><br><span>\${c.maxKw}kW · \${c.arriveSoc}% → \${c.departSoc}% · \${c.chargeMin}분</span></div>`)
        .addTo(map);
    }));
    if (pts.length) map.fitBounds(L.latLngBounds(pts).pad(0.15), { maxZoom: 13 });
  })();
</script>
</body>
</html>
