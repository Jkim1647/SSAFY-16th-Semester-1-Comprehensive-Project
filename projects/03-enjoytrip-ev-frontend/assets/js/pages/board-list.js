// F109 / F110 게시글 목록 (검색 + 페이지네이션)
import { $, escapeHtml, formatDate, getParam } from "../core/util.js";
import { currentUser } from "../core/auth.js";
import { BOARDS, boardType, canWrite, listPosts } from "../core/board.js";

const type = boardType(getParam("type"));
const user = currentUser();
const state = { query: getParam("q") ?? "", page: Number(getParam("page")) || 1 };

document.title = `${BOARDS[type].label} · EnjoyTrip EV`;
$("#board-title").textContent = BOARDS[type].label;
$("#board-desc").textContent = BOARDS[type].desc;
$("#q").value = state.query;

if (canWrite(user, type)) {
  $("#btn-write").href = `board-write.html?type=${type}`;
  $("#btn-write").classList.remove("d-none");
}

const rowHtml = (p, pinned = false) => `
  <tr class="${pinned ? "is-notice" : ""}">
    <td class="small text-body-secondary">${pinned ? "📌" : p.no}</td>
    <td class="col-title"><a href="board-view.html?id=${encodeURIComponent(p.id)}">${pinned ? "[공지] " : ""}${escapeHtml(p.title)}</a></td>
    <td class="small text-body-secondary">${escapeHtml(p.writerName)}</td>
    <td class="small text-body-secondary">${formatDate(p.createdAt)}</td>
    <td class="small text-body-secondary">${p.views ?? 0}</td>
  </tr>`;

const render = () => {
  const { items, total, totalPages, page } = listPosts({ type, query: state.query, page: state.page });
  state.page = page;
  // 자유게시판 첫 페이지 맨 위에 최신 공지 1건 고정
  const pinned = type === "free" && page === 1 && !state.query ? listPosts({ type: "notice", size: 1 }).items : [];

  $("#post-rows").innerHTML =
    pinned.map((p) => rowHtml(p, true)).join("") +
    (items.length
      ? items.map((p) => rowHtml(p)).join("")
      : `<tr><td colspan="5" class="empty-state">${state.query ? "검색 결과가 없습니다." : "아직 게시글이 없습니다."}</td></tr>`);

  const link = (p, label, disabled = false, active = false) =>
    `<li class="page-item ${disabled ? "disabled" : ""} ${active ? "active" : ""}">
      <a class="page-link" href="?type=${type}&page=${p}${state.query ? `&q=${encodeURIComponent(state.query)}` : ""}"
        ${active ? 'aria-current="page"' : ""} ${disabled ? 'tabindex="-1" aria-disabled="true"' : ""}>${label}</a></li>`;
  $("#pager").innerHTML = total
    ? link(page - 1, "‹ 이전", page === 1) +
      Array.from({ length: totalPages }, (_, i) => link(i + 1, i + 1, false, i + 1 === page)).join("") +
      link(page + 1, "다음 ›", page === totalPages)
    : "";
};

$("#search-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const q = $("#q").value.trim();
  location.search = `?type=${type}${q ? `&q=${encodeURIComponent(q)}` : ""}`;
});

render();
