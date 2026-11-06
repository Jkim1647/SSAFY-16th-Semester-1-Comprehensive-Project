<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="hotplace" />
<!doctype html>
<html lang="ko">
<head>
<title><c:out value="${hp.title}" /> · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<%@ include file="../fragments/leaflet.jspf" %>
<link rel="stylesheet" href="${root}/assets/css/hotplace.css">
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="page-wrap" style="max-width:1100px">
  <nav aria-label="breadcrumb" class="small mb-2"><a href="${root}/hotplace">← 핫플레이스 목록</a></nav>
  <div class="row g-4">
    <div class="col-lg-7">
      <span class="type-tag ${hp.placeType == '충전소' ? 'is-ev' : ''}"><c:out value="${hp.placeType}" /></span>
      <h1 class="page-title mb-3"><c:out value="${hp.title}" /></h1>
      <c:choose>
        <c:when test="${not empty hp.photoFile}">
          <img class="detail-photo mb-3" src="${root}/uploads/${fn:escapeXml(hp.photoFile)}" alt="<c:out value='${hp.title}'/> 사진">
        </c:when>
        <c:otherwise><div class="detail-photo mb-3 d-grid text-body-secondary" style="place-items:center">사진 없음</div></c:otherwise>
      </c:choose>
      <dl class="kv mb-3">
        <dt>다녀온 날</dt><dd>${hp.visitedOn}</dd>
        <dt>등록자</dt><dd><c:out value="${hp.writerName}" /></dd>
        <dt>등록일</dt><dd>${fn:substring(fn:replace(hp.createdAt, 'T', ' '), 0, 16)}</dd>
        <dt>위치</dt><dd><fmt:formatNumber value="${hp.latitude}" maxFractionDigits="5" />, <fmt:formatNumber value="${hp.longitude}" maxFractionDigits="5" /></dd>
      </dl>
      <c:choose>
        <c:when test="${not empty hp.description}"><p class="hp-body mb-4"><c:out value="${hp.description}" /></p></c:when>
        <c:otherwise><p class="text-body-secondary mb-4">설명이 없습니다.</p></c:otherwise>
      </c:choose>

      <div class="d-flex flex-wrap gap-2">
        <a class="btn btn-outline-primary" href="${root}/ev?lat=${hp.latitude}&lng=${hp.longitude}">근처 충전소 예약</a>
        <c:if test="${canEdit}">
          <a class="btn btn-outline-secondary" href="${root}/hotplace?action=edit-form&id=${hp.hotplaceId}">수정</a>
          <form method="post" action="${root}/hotplace?action=delete&id=${hp.hotplaceId}" class="d-inline"
            onsubmit="return confirm('이 핫플레이스를 삭제할까요? 사진도 함께 지워집니다.')">
            <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
            <button class="btn btn-outline-danger" type="submit">삭제</button>
          </form>
        </c:if>
      </div>
    </div>
    <div class="col-lg-5">
      <div id="map" class="hp-detail-map mb-3" aria-label="핫플레이스 위치 지도"></div>
    </div>
  </div>
</main>
<script type="application/json" id="hp-data">${hpJson}</script>
<%@ include file="../fragments/footer.jsp" %>
<script>
  const hp = ET.data("hp-data");
  const map = ET.createMap("map", { lat: hp.lat, lng: hp.lng, zoom: 14 });
  L.marker([hp.lat, hp.lng], { title: hp.title }).addTo(map);
</script>
</body>
</html>
