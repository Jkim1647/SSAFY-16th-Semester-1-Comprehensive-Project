// F109 / F110 게시글 등록 · 수정
import { $, getParam, setFieldError } from "../core/util.js";
import { requireLogin } from "../core/auth.js";
import { BOARDS, LIMITS, boardType, canEdit, canWrite, createPost, getPost, updatePost } from "../core/board.js";

const user = requireLogin();
const editId = getParam("id");
const editing = editId ? getPost(editId) : null;
const type = editing ? editing.type : boardType(getParam("type"));

// 권한 확인: 수정은 작성자(공지는 관리자), 작성은 게시판별 규칙
const allowed = user && (editing ? canEdit(user, editing) : !editId && canWrite(user, type));
if (user && !allowed) {
  alert(editId && !editing ? "게시글을 찾을 수 없습니다." : "이 작업을 할 권한이 없습니다.");
  location.replace(`board.html?type=${type}`);
}

$("#board-label").textContent = BOARDS[type].label;
$("#form-title").textContent = editing ? "글 수정" : "글쓰기";
$("#btn-submit").textContent = editing ? "수정" : "등록";
$("#btn-cancel").href = editing ? `board-view.html?id=${encodeURIComponent(editing.id)}` : `board.html?type=${type}`;
document.title = `${editing ? "글 수정" : "글쓰기"} · ${BOARDS[type].label}`;

const titleEl = $("#title");
const contentEl = $("#content");
const updateCount = () => ($("#count").textContent = contentEl.value.length);
contentEl.addEventListener("input", updateCount);

if (editing) {
  titleEl.value = editing.title;
  contentEl.value = editing.content;
}
updateCount();

$("#post-form").addEventListener("submit", (e) => {
  e.preventDefault();
  if (!allowed) return;
  const title = titleEl.value.trim();
  const content = contentEl.value.trim();
  const ok = [
    setFieldError(titleEl, !title ? "제목을 입력하세요." : title.length > LIMITS.title ? `제목은 ${LIMITS.title}자 이내입니다.` : ""),
    setFieldError(contentEl, !content ? "내용을 입력하세요." : content.length > LIMITS.content ? `내용은 ${LIMITS.content}자 이내입니다.` : ""),
  ];
  if (ok.includes(false)) return $("#post-form .is-invalid")?.focus();

  const saved = editing ? updatePost(editing.id, { title, content }) : createPost({ type, title, content, user });
  location.replace(`board-view.html?id=${encodeURIComponent(saved.id)}`);
});
