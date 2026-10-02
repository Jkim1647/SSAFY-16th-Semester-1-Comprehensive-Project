// F108 로그인
import { $, setFieldError } from "../core/util.js";
import { currentUser, login } from "../core/auth.js";

const REMEMBER_KEY = "enjoytrip.rememberId";
const form = $("#login-form");
// 주의: form.id / form.name 은 폼 자신의 속성이라 입력 요소를 가리키지 않는다 → 직접 선택
const idInput = $("#id");
const pwInput = $("#password");
const alertBox = $("#form-alert");

// 오픈 리다이렉트 방지: 같은 사이트의 페이지 이름만 허용
const safeRedirect = () => {
  const target = new URLSearchParams(location.search).get("redirect") ?? "";
  return /^[a-z-]+\.html(\?[\w=&%-]*)?$/.test(target) ? target : "index.html";
};

if (currentUser()) location.replace(safeRedirect());

const saved = localStorage.getItem(REMEMBER_KEY);
if (saved) {
  idInput.value = saved;
  $("#remember").checked = true;
}

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  alertBox.classList.add("d-none");
  const id = idInput.value.trim();
  const password = pwInput.value;
  const ok = [
    setFieldError(idInput, id ? "" : "아이디를 입력하세요."),
    setFieldError(pwInput, password ? "" : "비밀번호를 입력하세요."),
  ];
  if (ok.includes(false)) return;

  try {
    await login(id, password);
    if ($("#remember").checked) localStorage.setItem(REMEMBER_KEY, id);
    else localStorage.removeItem(REMEMBER_KEY);
    location.replace(safeRedirect());
  } catch (err) {
    alertBox.textContent = err.message;
    alertBox.classList.remove("d-none");
    pwInput.value = "";
    pwInput.focus();
  }
});
