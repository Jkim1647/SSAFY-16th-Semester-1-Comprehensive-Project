<%@ page contentType="text/html; charset=UTF-8"%>
<!doctype html>
<html lang="ko">
<head>
<title>비밀번호 찾기 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="auth-wrap">
  <h1 class="mb-2">비밀번호 찾기</h1>
  <c:choose>
    <c:when test="${sent}">
      <div class="alert alert-success" role="status">
        <p class="mb-2">입력한 정보와 일치하는 계정이 있으면, 가입한 이메일로 비밀번호 재설정 링크를 보냈습니다.</p>
        <p class="small mb-0">링크는 15분 동안 한 번만 쓸 수 있습니다. 기존 비밀번호는 재설정을 마칠 때까지 그대로입니다.</p>
      </div>
      <p class="small text-body-secondary">개발 환경에는 메일 서버가 없어 링크가 서버 콘솔(Tomcat 로그)에 출력됩니다.</p>
      <a class="btn btn-outline-primary w-100" href="${root}/member?action=login-form">로그인 화면으로</a>
    </c:when>
    <c:otherwise>
      <p class="text-body-secondary small mb-4">가입한 이메일과 이름을 입력하면 재설정 링크를 이메일로 보내 드립니다.</p>
      <c:if test="${not empty error}">
        <div class="alert alert-danger py-2" role="alert"><c:out value="${error}" /></div>
      </c:if>
      <form method="post" action="${root}/member?action=find-password" class="vstack gap-3">
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <div>
          <label class="form-label" for="email">이메일</label>
          <input class="form-control" type="email" id="email" name="email" required autocomplete="email">
        </div>
        <div>
          <label class="form-label" for="name">이름</label>
          <input class="form-control" type="text" id="name" name="name" required autocomplete="name">
        </div>
        <button class="btn btn-primary w-100" type="submit">재설정 링크 받기</button>
      </form>
    </c:otherwise>
  </c:choose>
</main>
<%@ include file="../fragments/footer.jsp" %>
</body>
</html>
