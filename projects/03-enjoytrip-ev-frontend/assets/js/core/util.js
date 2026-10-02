/** 사용자 입력/외부 API 문자열을 innerHTML 에 넣기 전에 반드시 거친다. */
export const escapeHtml = (value) =>
  String(value ?? "").replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[ch]);

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

export const getParam = (name) => new URLSearchParams(location.search).get(name);

export const uid = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;

export const formatDate = (value, withTime = false) => {
  if (!value) return "";
  const d = new Date(value);
  const pad = (n) => String(n).padStart(2, "0");
  const date = `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`;
  return withTime ? `${date} ${pad(d.getHours())}:${pad(d.getMinutes())}` : date;
};

export const formatWon = (n) => `${Number(n || 0).toLocaleString("ko-KR")}원`;

export const debounce = (fn, wait = 300) => {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), wait);
  };
};

/** 화면 우하단 토스트 메시지 (Bootstrap Toast) */
export const toast = (message, variant = "dark") => {
  let box = document.getElementById("toast-box");
  if (!box) {
    box = document.createElement("div");
    box.id = "toast-box";
    box.className = "toast-container position-fixed bottom-0 end-0 p-3";
    box.setAttribute("aria-live", "polite");
    document.body.appendChild(box);
  }
  const el = document.createElement("div");
  el.className = `toast align-items-center text-bg-${variant} border-0`;
  el.setAttribute("role", "status");
  el.innerHTML = `<div class="d-flex"><div class="toast-body">${escapeHtml(message)}</div>
    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="닫기"></button></div>`;
  box.appendChild(el);
  el.addEventListener("hidden.bs.toast", () => el.remove());
  new bootstrap.Toast(el, { delay: 2500 }).show();
};

/** 이미지 파일을 최대 maxSize px 로 줄여 dataURL 로 반환 (localStorage 용량 절약) */
export const resizeImage = (file, maxSize = 800, quality = 0.8) =>
  new Promise((resolve, reject) => {
    if (!file.type.startsWith("image/")) {
      reject(new Error("이미지 파일만 등록할 수 있습니다."));
      return;
    }
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error("이미지를 읽을 수 없습니다."));
      img.onload = () => {
        const scale = Math.min(1, maxSize / Math.max(img.width, img.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.round(img.width * scale);
        canvas.height = Math.round(img.height * scale);
        canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/jpeg", quality));
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });

/** Bootstrap 폼 필드에 검증 결과 표시 */
export const setFieldError = (input, message) => {
  const feedback = input.parentElement.querySelector(".invalid-feedback");
  if (message) {
    input.classList.add("is-invalid");
    input.classList.remove("is-valid");
    if (feedback) feedback.textContent = message;
  } else {
    input.classList.remove("is-invalid");
    input.classList.add("is-valid");
  }
  return !message;
};
