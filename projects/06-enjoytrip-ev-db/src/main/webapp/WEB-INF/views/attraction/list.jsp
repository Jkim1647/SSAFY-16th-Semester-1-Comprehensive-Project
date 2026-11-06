<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="attraction" />
<!doctype html>
<html lang="ko">
<head>
<title>관광지 조회 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<%@ include file="../fragments/leaflet.jspf" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="app-shell">
  <section class="app-panel" aria-labelledby="panel-title">
    <div class="app-panel__head">
      <h1 id="panel-title" class="h5 fw-bold mb-3">관광지 조회</h1>
      <form method="get" action="${root}/attraction" class="vstack gap-2" role="search" id="search-form">
        <div class="input-group">
          <label class="visually-hidden" for="keyword">키워드</label>
          <input id="keyword" name="keyword" class="form-control" type="search" value="<c:out value='${param.keyword}'/>"
            placeholder="관광지 이름으로 검색 (예: 무등산)" maxlength="30">
          <button class="btn btn-primary" type="submit">검색</button>
        </div>
        <label class="visually-hidden" for="signgu">시군구</label>
        <select id="signgu" name="signgu" class="form-select form-select-sm" onchange="this.form.submit()">
          <option value="">광주·전남 전체</option>
          <c:forEach var="s" items="${signguList}">
            <option value="${s.code}" ${param.signgu == s.code ? 'selected' : ''}><c:out value="${s.name}" /></option>
          </c:forEach>
        </select>
        <fieldset>
          <legend class="visually-hidden">콘텐츠 유형</legend>
          <div class="chip-row">
            <input type="radio" class="btn-check" name="type" id="type-all" value="" ${empty param.type ? 'checked' : ''} onchange="this.form.submit()">
            <label class="btn btn-outline-secondary" for="type-all">전체</label>
            <c:forEach var="t" items="${contentTypes}">
              <input type="radio" class="btn-check" name="type" id="type-${t.code}" value="${t.code}" ${param.type == t.code ? 'checked' : ''} onchange="this.form.submit()">
              <label class="btn btn-outline-secondary" for="type-${t.code}"><c:out value="${t.name}" /></label>
            </c:forEach>
          </div>
        </fieldset>
      </form>
    </div>
    <div class="app-panel__body">
      <p class="result-meta mb-0" aria-live="polite">
        <fmt:formatNumber value="${page.totalItems}" />곳 · ${page.condition.currentPage}/${page.totalPages} 페이지
      </p>
      <c:choose>
        <c:when test="${empty page.list}">
          <div class="empty-state">조건에 맞는 관광지가 없습니다.<br>지역이나 유형을 바꿔 보세요.</div>
        </c:when>
        <c:otherwise>
          <ul class="spot-list">
            <c:forEach var="a" items="${page.list}" varStatus="st">
              <li>
                <a class="spot-item text-decoration-none" href="${root}/attraction?action=detail&contentId=${a.contentId}" data-idx="${st.index}">
                  <c:choose>
                    <c:when test="${not empty a.firstImage2}"><img class="spot-thumb" src="${fn:escapeXml(a.firstImage2)}" alt="" loading="lazy"></c:when>
                    <c:otherwise><span class="spot-thumb">사진 없음</span></c:otherwise>
                  </c:choose>
                  <span class="min-w-0">
                    <span class="type-tag"><c:out value="${a.contentTypeName}" /></span>
                    <span class="spot-title d-block"><c:out value="${a.title}" /></span>
                    <span class="spot-addr d-block text-truncate"><c:out value="${a.addr1}" /></span>
                  </span>
                  <span class="text-body-secondary small">${st.index + 1}</span>
                </a>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
      <c:set var="pageUrl" value="${root}/attraction?keyword=${fn:escapeXml(param.keyword)}&signgu=${fn:escapeXml(param.signgu)}&type=${fn:escapeXml(param.type)}&page=" />
      <div class="px-3 pb-3"><%@ include file="../fragments/pagination.jsp" %></div>
    </div>
  </section>
  <section class="app-map" aria-label="지도">
    <span class="map-badge">현재 페이지 ${fn:length(page.list)}곳 표시</span>
    <div id="map" class="map"></div>
  </section>
</main>
<script type="application/json" id="spots-data">${spotsJson}</script>
<%@ include file="../fragments/scripts.jspf" %>
<script>
  const spots = ET.data("spots-data");
  const map = ET.createMap("map");
  const markers = spots.map((s, i) => {
    const icon = L.divIcon({ className: "", html: `<div class="num-marker"><span>\${i + 1}</span></div>`, iconSize: [28, 28], iconAnchor: [14, 28] });
    return L.marker([s.lat, s.lng], { icon })
      .bindPopup(`<div class="map-iw"><strong>\${ET.escapeHtml(s.title)}</strong><br><span>\${ET.escapeHtml(s.type)} · \${ET.escapeHtml(s.addr)}</span></div>`)
      .addTo(map);
  });
  if (markers.length) map.fitBounds(L.featureGroup(markers).getBounds().pad(0.2), { maxZoom: 14 });
  document.querySelectorAll(".spot-item").forEach((el) => {
    el.addEventListener("mouseenter", () => markers[el.dataset.idx]?.openPopup());
  });
</script>
</body>
</html>
