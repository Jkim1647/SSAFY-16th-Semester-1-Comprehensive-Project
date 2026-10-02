// F108 비밀번호 찾기 (메일 발송 서버가 없으므로 아이디+이메일 확인 후 바로 재설정)
import { $, setFieldError, toast } from "../core/util.js";
import { canResetPassword, resetPassword, validate } from "../core/auth.js";

const verifyForm = $("#verify-form");
const resetForm = $("#reset-form");
const alertBox = $("#form-alert");
let verifiedId = null;

verifyForm.addEventListener("submit", (e) => {
  e.preventDefault();
  alertBox.classList.add("d-none");
  const id = $("#id").value.trim();
  const email = $("#email").value.trim();
  const ok = [
    setFieldError($("#id"), id ? "" : "아이디를 입력하세요."),
    setFieldError($("#email"), validate("email", email)),
  ];
  if (ok.includes(false)) return;

  if (!canResetPassword(id, email)) {
    alertBox.textContent = "일치하는 회원 정보가 없습니다.";
    alertBox.classList.remove("d-none");
    return;
  }
  verifiedId = id;
  verifyForm.classList.add("d-none");
  resetForm.classList.remove("d-none");
  $("#new-password").focus();
});

resetForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const pw = $("#new-password").value;
  const ok = [
    setFieldError($("#new-password"), validate("password", pw)),
    setFieldError($("#new-password2"), pw === $("#new-password2").value ? "" : "비밀번호가 일치하지 않습니다."),
  ];
  if (ok.includes(false) || !verifiedId) return;
  await resetPassword(verifiedId, pw);
  toast("비밀번호가 변경되었습니다.", "success");
  setTimeout(() => location.replace("login.html"), 800);
});
