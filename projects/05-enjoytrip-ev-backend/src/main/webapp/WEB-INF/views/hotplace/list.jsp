<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="hotplace" />
<!doctype html>
<html lang="ko">
<head>
<title>핫플레이스 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<%@ include file="../fragments/leaflet.jspf" %>
<link rel="stylesheet" href="${root}/assets/css/hotplace.css">
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="app-shell">
  <section class="app-panel" aria-labelledby="panel-title">
    <div class="app-panel__head">
      <div class="d-flex align-items-center mb-3">
        <h1 id="panel-title" class="h5 fw-bold mb-0 flex-fill">핫플레이스</h1>
        <a class="btn btn-primary btn-sm" href="${root}/hotplace?action=regist-form">+ 등록</a>
      </div>
      <form method="get" action="${root}/hotplace" id="filter-form" class="vstack gap-2">
        <fieldset>
          <legend class="visually-hidden">장소 유형</legend>
          <div class="chip-row">
            <input type="radio" class="btn-check" name="type" id="type-all" value="" ${empty selectedType ? 'checked' : ''} onchange="this.form.submit()">
            <label class="btn btn-outline-secondary" for="type-all">전체</label>
            <c:forEach var="t" items="${placeTypes}" varStatus="st">
              <input type="radio" class="btn-check" name="type" id="type-${st.index}" value="${t}" ${selectedType == t ? 'checked' : ''} onchange="this.form.submit()">
              <label class="btn btn-outline-secondary" for="type-${st.index}">${t}</label>
            </c:forEach>
          </div>
        </fieldset>
        <c:if test="${not empty sessionScope.loginUser}">
          <div class="form-check form-switch small mb-0">
            <input class="form-check-input" type="checkbox" role="switch" id="mine" name="mine" value="1" ${mine ? 'checked' : ''} onchange="this.form.submit()">
            <label class="form-check-label" for="mine">내 핫플만 보기</label>
          </div>
        </c:if>
        <noscript><button class="btn btn-sm btn-outline-secondary" type="submit">적용</button></noscript>
      </form>
    </div>
    <div class="app-panel__body">
      <p class="result-meta mb-0" aria-live="polite">
        핫플레이스 <fmt:formatNumber value="${page.totalItems}" />곳 · ${page.condition.currentPage}/${page.totalPages} 페이지
      </p>
      <c:choose>
        <c:when test="${empty page.list}">
          <div class="empty-state">
            <c:choose>
              <c:when test="${mine}">아직 내가 등록한 핫플레이스가 없습니다.</c:when>
              <c:otherwise>아직 등록된 핫플레이스가 없습니다.</c:otherwise>
            </c:choose>
            <br>다녀온 숨은 명소를 처음으로 소개해 주세요.
          </div>
        </c:when>
        <c:otherwise>
          <div class="vstack gap-3 px-3 pb-3">
            <c:forEach var="h" items="${page.list}">
              <article class="hp-card" data-id="${h.hotplaceId}">
                <c:choose>
                  <c:when test="${not empty h.photoFile}">
                    <img class="hp-photo" src="${root}/uploads/${fn:escapeXml(h.photoFile)}" alt="<c:out value='${h.title}'/> 사진" loading="lazy">
                  </c:when>
                  <c:otherwise><div class="hp-photo" aria-hidden="true">사진 없음</div></c:otherwise>
                </c:choose>
                <div class="p-3">
                  <span class="type-tag ${h.placeType == '충전소' ? 'is-ev' : ''}"><c:out value="${h.placeType}" /></span>
                  <h2 class="h6 fw-bold mt-1 mb-1">
                    <a class="text-body text-decoration-none" href="${root}/hotplace?action=detail&id=${h.hotplaceId}"><c:out value="${h.title}" /></a>
                  </h2>
                  <p class="small text-body-secondary mb-1">${h.visitedOn} 방문 · <c:out value="${h.writerName}" /></p>
                  <c:if test="${not empty h.description}"><p class="small mb-2 hp-desc"><c:out value="${h.description}" /></p></c:if>
                  <div class="d-flex gap-2">
                    <button class="btn btn-sm btn-outline-primary" type="button" data-focus="${h.hotplaceId}">지도에서 보기</button>
                    <a class="btn btn-sm btn-outline-secondary" href="${root}/hotplace?action=detail&id=${h.hotplaceId}">자세히</a>
                  </div>
                </div>
              </article>
            </c:forEach>
          </div>
        </c:otherwise>
      </c:choose>
      <c:url var="baseUrl" value="/hotplace">
        <c:if test="${not empty selectedType}"><c:param name="type" value="${selectedType}" /></c:if>
        <c:if test="${mine}"><c:param name="mine" value="1" /></c:if>
      </c:url>
      <c:set var="pageUrl" value="${baseUrl}${fn:contains(baseUrl, '?') ? '&' : '?'}page=" />
      <div class="px-3 pb-3"><%@ include file="../fragments/pagination.jsp" %></div>
    </div>
  </section>
  <section class="app-map" aria-label="지도">
    <span class="map-badge">지도에 ${markerCount}곳 표시</span>
    <div id="map" class="map"></div>
  </section>
</main>
<script type="application/json" id="markers-data">${markersJson}</script>
<%@ include file="../fragments/scripts.jspf" %>
<script>
  const places = ET.data("markers-data");
  const map = ET.createMap("map");
  const byId = new Map();
  const highlight = (id) => {
    document.querySelectorAll(".hp-card").forEach((c) => c.classList.toggle("is-active", c.dataset.id === String(id)));
    document.querySelector(`.hp-card[data-id="\${id}"]`)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  };
  places.forEach((p) => {
    const photo = p.photo ? `<img src="\${ET.ctx}/uploads/\${encodeURIComponent(p.photo)}" alt="">` : "";
    const marker = L.marker([p.lat, p.lng], { title: p.title })
      .bindPopup(`<div class="map-iw hp-popup">\${photo}<strong>\${ET.escapeHtml(p.title)}</strong><br>
        <span>\${ET.escapeHtml(p.type)} · \${ET.escapeHtml(p.visitedOn)} 방문</span><br>
        <a href="\${ET.ctx}/hotplace?action=detail&id=\${p.id}">자세히 보기</a></div>`)
      .on("click", () => highlight(p.id))
      .addTo(map);
    byId.set(String(p.id), marker);
  });
  if (byId.size) map.fitBounds(L.featureGroup([...byId.values()]).getBounds().pad(0.2), { maxZoom: 14 });

  document.querySelectorAll("[data-focus]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const marker = byId.get(btn.dataset.focus);
      if (!marker) return;
      map.setView(marker.getLatLng(), Math.max(map.getZoom(), 14));
      marker.openPopup();
      highlight(btn.dataset.focus);
    });
  });
</script>
</body>
</html>
