// F107 회원가입 + 폼 유효성 검사
import { $, setFieldError, toast } from "../core/util.js";
import { isIdTaken, signup, validate } from "../core/auth.js";

const form = $("#signup-form");
const f = {
  id: $("#id"),
  password: $("#password"),
  password2: $("#password2"),
  name: $("#name"),
  email: $("#email"),
};
let checkedId = null; // 중복 확인을 통과한 아이디

const checks = {
  id: () => {
    const v = f.id.value.trim();
    const msg = validate("id", v) || (checkedId !== v ? "아이디 중복 확인을 해 주세요." : "");
    return setFieldError(f.id, msg);
  },
  password: () => setFieldError(f.password, validate("password", f.password.value)),
  password2: () =>
    setFieldError(f.password2, f.password2.value && f.password2.value === f.password.value ? "" : "비밀번호가 일치하지 않습니다."),
  name: () => setFieldError(f.name, validate("name", f.name.value.trim())),
  email: () => setFieldError(f.email, validate("email", f.email.value.trim())),
};

$("#btn-check-id").addEventListener("click", () => {
  const v = f.id.value.trim();
  const msg = validate("id", v) || (isIdTaken(v) ? "이미 사용 중인 아이디입니다." : "");
  checkedId = msg ? null : v;
  setFieldError(f.id, msg);
  $("#id-hint").textContent = msg ? "영문 소문자/숫자 4~16자" : "사용할 수 있는 아이디입니다.";
});

// 아이디를 바꾸면 중복 확인을 다시 받아야 한다
f.id.addEventListener("input", () => {
  checkedId = null;
  f.id.classList.remove("is-valid");
  $("#id-hint").textContent = "영문 소문자/숫자 4~16자";
});

// 입력을 마칠 때마다 해당 필드만 검사 (아이디는 중복 확인 버튼으로 검사)
["password", "password2", "name", "email"].forEach((key) => f[key].addEventListener("blur", checks[key]));
f.password.addEventListener("input", () => f.password2.value && checks.password2());

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  const results = Object.values(checks).map((check) => check());
  if (results.includes(false)) {
    form.querySelector(".is-invalid")?.focus();
    return;
  }
  try {
    await signup({
      id: f.id.value.trim(),
      password: f.password.value,
      name: f.name.value.trim(),
      email: f.email.value.trim(),
    });
    toast("가입이 완료되었습니다. 로그인해 주세요.", "success");
    setTimeout(() => location.replace("login.html"), 800);
  } catch (err) {
    setFieldError(f.id, err.message);
  }
});
