import { $, escapeHtml } from "../core/util.js";
import { CONTENT_TYPES, getSidoList } from "../core/tour-api.js";

$("#qs-type").innerHTML = CONTENT_TYPES.map((t) => `<option value="${t.id}">${t.label}</option>`).join("");

getSidoList()
  .then((rows) => {
    $("#qs-sido").innerHTML += rows
      .map((r) => `<option value="${escapeHtml(r.code)}">${escapeHtml(r.name)}</option>`)
      .join("");
  })
  .catch((e) => console.warn("시도 목록을 불러오지 못했습니다.", e));

// 빈 값 파라미터는 빼고 넘긴다 (attraction.html?keyword=&sido=… 방지)
$(".quick-search").addEventListener("submit", (e) => {
  e.preventDefault();
  const params = new URLSearchParams(new FormData(e.currentTarget));
  [...params.keys()].forEach((k) => !params.get(k) && params.delete(k));
  location.href = `attraction.html${params.size ? `?${params}` : ""}`;
});
