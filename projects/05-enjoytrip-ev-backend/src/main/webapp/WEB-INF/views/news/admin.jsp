<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="news" />
<!doctype html>
<html lang="ko">
<head>
<title>뉴스 수집 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="page-wrap" style="max-width:800px">
  <p class="small fw-medium text-primary mb-1">관리자</p>
  <h1 class="page-title mb-2">뉴스 수집</h1>
  <p class="small text-body-secondary mb-4">검색어마다 Google 뉴스 RSS에서 최신 기사를 최대 30건씩 가져옵니다. 이미 저장된 기사(같은 링크)는 건너뜁니다.</p>

  <c:if test="${not empty results}">
    <section class="mb-4" aria-labelledby="result-title">
      <h2 class="h6 fw-bold" id="result-title">방금 수집한 결과</h2>
      <div class="table-responsive">
        <table class="table table-sm board-table mb-0">
          <thead><tr><th>검색어</th><th class="text-end">가져온 기사</th><th class="text-end">새로 저장</th><th>상태</th></tr></thead>
          <tbody>
            <c:forEach var="r" items="${results}">
              <tr>
                <td><c:out value="${r.keyword}" /></td>
                <td class="text-end">${r.fetched}</td>
                <td class="text-end fw-medium">${r.saved}</td>
                <td class="small">
                  <c:choose>
                    <c:when test="${r.success}"><span class="text-success">성공</span></c:when>
                    <c:otherwise><span class="text-danger"><c:out value="${r.error}" /></span></c:otherwise>
                  </c:choose>
                </td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
    </section>
  </c:if>

  <form method="post" action="${root}/news?action=crawl" class="vstack gap-3" id="crawl-form">
    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
    <div>
      <label class="form-label" for="keywords">검색어 <span class="small text-body-secondary fw-normal">(한 줄에 하나, 최대 10개 · 비우면 기본 검색어)</span></label>
      <textarea class="form-control" id="keywords" name="keywords" rows="6" maxlength="600"><c:out value="${defaultKeywords}" /></textarea>
    </div>
    <div class="d-flex gap-2 align-items-center">
      <button class="btn btn-primary" type="submit" id="crawl-btn">지금 수집</button>
      <a class="btn btn-outline-secondary" href="${root}/news">뉴스 목록 보기</a>
      <span class="small text-body-secondary d-none" id="crawl-wait" role="status">수집 중입니다. 검색어 수에 따라 수십 초 걸릴 수 있어요…</span>
    </div>
  </form>

  <c:if test="${not empty keywords}">
    <section class="mt-5" aria-labelledby="saved-title">
      <h2 class="h6 fw-bold" id="saved-title">저장된 기사 (검색어별)</h2>
      <ul class="list-inline mb-0">
        <c:forEach var="k" items="${keywords}">
          <c:url var="kwUrl" value="/news"><c:param name="keyword" value="${k.key}" /></c:url>
          <li class="list-inline-item"><a class="tech-chip text-decoration-none" href="${kwUrl}"><c:out value="${k.key}" /> ${k.value}건</a></li>
        </c:forEach>
      </ul>
    </section>
  </c:if>
</main>
<%@ include file="../fragments/footer.jsp" %>
<script>
  // 두 번 눌러 중복 수집되지 않도록 제출 후 버튼을 잠근다
  document.querySelector("#crawl-form").addEventListener("submit", () => {
    const btn = document.querySelector("#crawl-btn");
    btn.disabled = true;
    btn.textContent = "수집 중…";
    document.querySelector("#crawl-wait").classList.remove("d-none");
  });
</script>
</body>
</html>
