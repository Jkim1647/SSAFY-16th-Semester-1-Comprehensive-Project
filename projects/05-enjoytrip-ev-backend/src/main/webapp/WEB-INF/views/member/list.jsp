<%@ page contentType="text/html; charset=UTF-8"%>
<!doctype html>
<html lang="ko">
<head>
<title>회원 관리 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="page-wrap">
  <div class="d-flex flex-wrap justify-content-between align-items-end gap-2 mb-3">
    <h1 class="page-title mb-0">회원 관리 <small class="fs-6 text-body-secondary">총 ${page.totalItems}명</small></h1>
    <form class="d-flex gap-2" method="get" action="${root}/member" role="search">
      <input type="hidden" name="action" value="list">
      <label class="visually-hidden" for="key">검색 항목</label>
      <select class="form-select form-select-sm" id="key" name="key" style="width:auto">
        <option value="name" ${param.key == 'name' ? 'selected' : ''}>이름</option>
        <option value="email" ${param.key == 'email' ? 'selected' : ''}>이메일</option>
      </select>
      <label class="visually-hidden" for="word">검색어</label>
      <input class="form-control form-control-sm" id="word" name="word" value="<c:out value='${param.word}'/>" maxlength="50">
      <button class="btn btn-sm btn-primary text-nowrap" type="submit">검색</button>
    </form>
  </div>
  <div class="table-responsive">
    <table class="table table-sm table-hover board-table">
      <thead><tr><th>번호</th><th>이름</th><th>이메일</th><th>권한</th><th>가입일</th><th></th></tr></thead>
      <tbody>
        <c:forEach var="m" items="${page.list}">
          <tr>
            <td>${m.memberId}</td>
            <td><c:out value="${m.name}" /></td>
            <td><c:out value="${m.email}" /></td>
            <td>${m.admin ? '관리자' : '회원'}</td>
            <td>${fn:substring(m.createdAt, 0, 10)}</td>
            <td class="text-end">
              <c:if test="${m.memberId != sessionScope.loginUser.memberId}">
                <form method="post" action="${root}/member?action=admin-delete" onsubmit="return confirm('이 회원을 삭제할까요?')">
                  <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                  <input type="hidden" name="memberId" value="${m.memberId}">
                  <button class="btn btn-sm btn-outline-danger" type="submit">삭제</button>
                </form>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty page.list}">
          <tr><td colspan="6" class="empty-state">조건에 맞는 회원이 없습니다.</td></tr>
        </c:if>
      </tbody>
    </table>
  </div>
  <c:set var="pageUrl" value="${root}/member?action=list&key=${fn:escapeXml(param.key)}&word=${fn:escapeXml(param.word)}&page=" />
  <%@ include file="../fragments/pagination.jsp" %>
</main>
<%@ include file="../fragments/footer.jsp" %>
</body>
</html>
