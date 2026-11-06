<%@ page contentType="text/html; charset=UTF-8"%>
<!doctype html>
<html lang="ko">
<head>
<title>회원가입 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="auth-wrap">
  <h1 class="mb-4">회원가입</h1>
  <c:if test="${not empty error}">
    <div class="alert alert-danger py-2" role="alert"><c:out value="${error}" /></div>
  </c:if>
  <form method="post" action="${root}/member?action=regist" class="vstack gap-3" id="regist-form" novalidate>
    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
    <div>
      <label class="form-label" for="email">이메일</label>
      <input class="form-control" type="email" id="email" name="email" value="<c:out value='${email}'/>" required maxlength="100" autocomplete="email">
      <div class="invalid-feedback" id="email-feedback">이미 사용 중인 이메일입니다.</div>
      <div class="valid-feedback">사용할 수 있는 이메일입니다.</div>
    </div>
    <div>
      <label class="form-label" for="name">이름</label>
      <input class="form-control" type="text" id="name" name="name" value="<c:out value='${name}'/>" required maxlength="30" autocomplete="name">
    </div>
    <div>
      <label class="form-label" for="pass">비밀번호</label>
      <input class="form-control" type="password" id="pass" name="pass" required minlength="8" maxlength="64" autocomplete="new-password" aria-describedby="pass-hint">
      <div class="form-hint mt-1" id="pass-hint">8~64자, 영문과 숫자를 모두 넣어 주세요.</div>
    </div>
    <div>
      <label class="form-label" for="pass2">비밀번호 확인</label>
      <input class="form-control" type="password" id="pass2" name="pass2" required autocomplete="new-password">
      <div class="invalid-feedback">비밀번호가 일치하지 않습니다.</div>
    </div>
    <button class="btn btn-primary w-100" type="submit">가입하기</button>
  </form>
</main>
<%@ include file="../fragments/footer.jsp" %>
<script>
  // 이메일 중복 확인 (AJAX). 입력이 멈춘 뒤 300ms 에 한 번만 물어서 타이핑마다 서버를 두드리지 않는다.
  const emailInput = document.querySelector("#email");
  const feedback = document.querySelector("#email-feedback");
  let timer;
  emailInput.addEventListener("input", (e) => {
    clearTimeout(timer);
    const target = e.target;
    target.classList.remove("is-valid", "is-invalid");
    if (!target.checkValidity() || !target.value) return;
    timer = setTimeout(async () => {
      try {
        const json = await ET.get(`/member?action=email-check&email=\${encodeURIComponent(target.value)}`);
        target.classList.toggle("is-valid", json.canUse);
        target.classList.toggle("is-invalid", !json.canUse);
        feedback.textContent = "이미 사용 중인 이메일입니다.";
      } catch (err) {
        console.error(err);
      }
    }, 300);
  });

  const pass = document.querySelector("#pass");
  const pass2 = document.querySelector("#pass2");
  pass2.addEventListener("input", () => pass2.classList.toggle("is-invalid", pass2.value !== pass.value));
  document.querySelector("#regist-form").addEventListener("submit", (e) => {
    if (emailInput.classList.contains("is-invalid") || pass2.value !== pass.value) {
      e.preventDefault();
      pass2.classList.toggle("is-invalid", pass2.value !== pass.value);
    }
  });
</script>
</body>
</html>
