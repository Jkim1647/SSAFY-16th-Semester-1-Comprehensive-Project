<%@ page contentType="text/html; charset=UTF-8"%>
<!doctype html>
<html lang="ko">
<head>
<title>로그인 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="auth-wrap">
  <h1 class="mb-4">로그인</h1>
  <c:if test="${not empty error}">
    <div class="alert alert-danger py-2" role="alert"><c:out value="${error}" /></div>
  </c:if>
  <form method="post" action="${root}/member?action=login" class="vstack gap-3">
    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
    <input type="hidden" name="next" value="<c:out value='${empty next ? param.next : next}'/>">
    <div>
      <label class="form-label" for="email">이메일</label>
      <input class="form-control" type="email" id="email" name="email" value="<c:out value='${email}'/>" required autocomplete="username" autofocus>
    </div>
    <div>
      <label class="form-label" for="pass">비밀번호</label>
      <input class="form-control" type="password" id="pass" name="pass" required autocomplete="current-password">
    </div>
    <button class="btn btn-primary w-100" type="submit">로그인</button>
  </form>
  <div class="d-flex justify-content-between mt-3 small">
    <a href="${root}/member?action=find-password-form">비밀번호를 잊으셨나요?</a>
    <a href="${root}/member?action=regist-form">회원가입</a>
  </div>
</main>
<%@ include file="../fragments/footer.jsp" %>
</body>
</html>
