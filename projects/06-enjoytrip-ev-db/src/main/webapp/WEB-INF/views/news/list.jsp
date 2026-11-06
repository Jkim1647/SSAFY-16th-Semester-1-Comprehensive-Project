<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="news" />
<!doctype html>
<html lang="ko">
<head>
<title>여행 뉴스 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="page-wrap" style="max-width:900px">
  <div class="d-flex flex-wrap justify-content-between align-items-end gap-2 mb-3">
    <div>
      <h1 class="page-title mb-1">여행 뉴스 <small class="fs-6 text-body-secondary">총 ${page.totalItems}건</small></h1>
      <p class="small text-body-secondary mb-0">광주·전남 관광지와 전기차 충전 관련 최신 기사를 모았습니다. 제목을 누르면 원문이 새 창으로 열립니다.</p>
    </div>
    <c:if test="${sessionScope.loginUser.admin}">
      <a class="btn btn-sm btn-outline-primary" href="${root}/news?action=admin">뉴스 수집</a>
    </c:if>
  </div>

  <c:if test="${not empty keywords}">
    <nav class="chip-row mb-3" aria-label="검색어로 거르기">
      <a class="btn btn-sm rounded-pill text-nowrap ${empty keyword ? 'btn-dark' : 'btn-outline-secondary'}"
        href="${root}/news" ${empty keyword ? 'aria-current="page"' : ''}>전체</a>
      <c:forEach var="k" items="${keywords}">
        <c:url var="chipUrl" value="/news"><c:param name="keyword" value="${k.key}" /></c:url>
        <a class="btn btn-sm rounded-pill text-nowrap ${k.key == keyword ? 'btn-dark' : 'btn-outline-secondary'}"
          href="${chipUrl}" ${k.key == keyword ? 'aria-current="page"' : ''}><c:out value="${k.key}" /> <span class="opacity-75">${k.value}</span></a>
      </c:forEach>
    </nav>
  </c:if>

  <c:choose>
    <c:when test="${empty page.list}">
      <div class="empty-state border rounded py-5">
        <c:choose>
          <c:when test="${not empty keyword}">이 검색어로 수집된 기사가 없습니다.</c:when>
          <c:otherwise>아직 수집된 뉴스가 없습니다.</c:otherwise>
        </c:choose>
      </div>
    </c:when>
    <c:otherwise>
      <ul class="list-group list-group-flush border-top border-bottom">
        <c:forEach var="n" items="${page.list}">
          <li class="list-group-item px-0 py-3">
            <c:choose>
              <c:when test="${n.safeLink}">
                <a class="fw-medium text-body text-decoration-none d-block mb-1" href="${fn:escapeXml(n.link)}"
                  target="_blank" rel="noopener noreferrer"><c:out value="${n.title}" /></a>
              </c:when>
              <c:otherwise><span class="fw-medium d-block mb-1"><c:out value="${n.title}" /></span></c:otherwise>
            </c:choose>
            <div class="small text-body-secondary d-flex flex-wrap gap-2">
              <span><c:out value="${empty n.source ? '출처 미상' : n.source}" /></span>
              <span aria-hidden="true">·</span>
              <span>${empty n.publishedAt ? '발행일 미상' : fn:replace(fn:substring(n.publishedAt, 0, 16), 'T', ' ')}</span>
              <span class="badge text-bg-light fw-normal"><c:out value="${n.keyword}" /></span>
            </div>
          </li>
        </c:forEach>
      </ul>
    </c:otherwise>
  </c:choose>

  <c:url var="pageUrl" value="/news"><c:param name="keyword" value="${keyword}" /></c:url>
  <c:set var="pageUrl" value="${pageUrl}&page=" />
  <%@ include file="../fragments/pagination.jsp" %>
  <p class="small text-body-secondary mt-4 mb-0">출처: Google 뉴스 RSS 검색 결과. 기사 저작권은 각 언론사에 있습니다.</p>
</main>
<%@ include file="../fragments/footer.jsp" %>
</body>
</html>
