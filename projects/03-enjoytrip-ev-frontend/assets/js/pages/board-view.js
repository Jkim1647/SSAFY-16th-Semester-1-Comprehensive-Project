// F109 / F110 게시글 상세 · 삭제
import { $, formatDate, getParam } from "../core/util.js";
import { currentUser } from "../core/auth.js";
import { BOARDS, canEdit, deletePost, increaseViews } from "../core/board.js";

const post = increaseViews(getParam("id") ?? "");

if (!post) {
  $("#post-title").textContent = "게시글을 찾을 수 없습니다.";
  $("#post-body").textContent = "삭제되었거나 잘못된 주소입니다.";
} else {
  const board = BOARDS[post.type];
  const listUrl = `board.html?type=${post.type}`;
  document.title = `${post.title} · ${board.label}`;
  $("#post-board").textContent = board.label;
  $("#post-board").href = listUrl;
  $("#btn-list").href = listUrl;
  $("#post-title").textContent = post.title;
  // textContent 로만 출력 → 본문에 태그가 있어도 실행되지 않는다
  $("#post-meta").innerHTML = "";
  [post.writerName, formatDate(post.createdAt, true) + (post.updatedAt ? " (수정됨)" : ""), `조회 ${post.views ?? 0}`].forEach((t) => {
    const span = document.createElement("span");
    span.textContent = t;
    $("#post-meta").appendChild(span);
  });
  $("#post-body").textContent = post.content;

  if (canEdit(currentUser(), post)) {
    $("#btn-edit").href = `board-write.html?id=${encodeURIComponent(post.id)}`;
    $("#btn-edit").classList.remove("d-none");
    $("#btn-delete").classList.remove("d-none");
    $("#btn-delete").addEventListener("click", () => {
      if (!confirm("이 게시글을 삭제할까요? 삭제한 글은 되돌릴 수 없습니다.")) return;
      deletePost(post.id);
      location.replace(listUrl);
    });
  }
}
