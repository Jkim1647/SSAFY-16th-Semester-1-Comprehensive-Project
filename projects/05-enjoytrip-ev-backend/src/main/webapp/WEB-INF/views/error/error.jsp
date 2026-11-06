<%@ page contentType="text/html; charset=UTF-8" isErrorPage="true"%>
<!doctype html>
<html lang="ko">
<head>
<title>오류 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<c:set var="code" value="${requestScope['jakarta.servlet.error.status_code']}" />
<main class="page-wrap text-center py-5">
  <p class="display-4 fw-bold mb-2">${empty code ? '500' : code}</p>
  <h1 class="h4 mb-3">
    <c:choose>
      <c:when test="${code == 404}">페이지를 찾을 수 없습니다.</c:when>
      <c:when test="${code == 403}">접근 권한이 없거나 요청이 만료되었습니다.</c:when>
      <c:otherwise>일시적인 오류가 발생했습니다.</c:otherwise>
    </c:choose>
  </h1>
  <c:if test="${not empty requestScope['jakarta.servlet.error.message'] && code == 403}">
    <p class="text-body-secondary"><c:out value="${requestScope['jakarta.servlet.error.message']}" /></p>
  </c:if>
  <a class="btn btn-primary mt-3" href="${root}/">메인으로</a>
</main>
<%@ include file="../fragments/footer.jsp" %>
</body>
</html>
