<%@ page contentType="text/html; charset=UTF-8"%>
<!doctype html>
<html lang="ko">
<head>
<title>비밀번호 재설정 · EnjoyTrip EV</title>
<meta name="referrer" content="no-referrer">
<%@ include file="../fragments/head.jsp" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="auth-wrap">
  <h1 class="mb-4">비밀번호 재설정</h1>
  <c:choose>
    <c:when test="${not valid}">
      <div class="alert alert-warning" role="alert">링크가 만료되었거나 이미 사용되었습니다.</div>
      <a class="btn btn-primary w-100" href="${root}/member?action=find-password-form">비밀번호 찾기 다시 하기</a>
    </c:when>
    <c:otherwise>
      <c:if test="${not empty error}">
        <div class="alert alert-danger py-2" role="alert"><c:out value="${error}" /></div>
      </c:if>
      <form method="post" action="${root}/member?action=reset-password" class="vstack gap-3">
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <input type="hidden" name="token" value="${fn:escapeXml(token)}">
        <div>
          <label class="form-label" for="pass">새 비밀번호</label>
          <input class="form-control" type="password" id="pass" name="pass" required minlength="8" maxlength="64" autocomplete="new-password" aria-describedby="pass-hint">
          <div class="form-hint mt-1" id="pass-hint">8~64자, 영문과 숫자를 모두 넣어 주세요.</div>
        </div>
        <div>
          <label class="form-label" for="pass2">새 비밀번호 확인</label>
          <input class="form-control" type="password" id="pass2" name="pass2" required autocomplete="new-password">
        </div>
        <button class="btn btn-primary w-100" type="submit">비밀번호 바꾸기</button>
      </form>
    </c:otherwise>
  </c:choose>
</main>
<%@ include file="../fragments/footer.jsp" %>
</body>
</html>
