// 모든 페이지 공통 유틸: 컨텍스트 경로, CSRF 헤더를 붙인 fetch, HTML 이스케이프, 지도 생성
(() => {
  const meta = (name) => document.querySelector(`meta[name="${name}"]`)?.content ?? "";
  const ctx = meta("ctx");

  const request = async (method, url, body) => {
    const headers = { "X-Requested-With": "fetch", Accept: "application/json" };
    let payload;
    if (body instanceof FormData) {
      payload = body;
    } else if (body !== undefined) {
      headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
      payload = new URLSearchParams(body);
    }
    if (method !== "GET") headers["X-CSRF-Token"] = meta("csrf-token");
    const res = await fetch(url.startsWith("http") ? url : ctx + url, { method, headers, body: payload });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || `요청 실패 (${res.status})`);
    return data;
  };

  const escapeHtml = (s) =>
    String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

  /** Leaflet 지도. 키 없이 동작하는 OpenStreetMap 기본 타일을 쓴다 (팀원 PC·포트가 달라도 도메인 등록 불필요). */
  const createMap = (el, { lat = 35.1468, lng = 126.9194, zoom = 11 } = {}) => {
    const map = L.map(el, { zoomControl: true }).setView([lat, lng], zoom);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(map);
    return map;
  };

  window.ET = {
    ctx,
    get: (url) => request("GET", url),
    post: (url, body) => request("POST", url, body),
    escapeHtml,
    createMap,
    /** <script type="application/json" id="..."> 에 서버가 넣어 둔 데이터를 읽는다 */
    data: (id) => JSON.parse(document.getElementById(id)?.textContent || "null"),
  };

  document.querySelectorAll(".toast.show").forEach((el) => bootstrap.Toast.getOrCreateInstance(el).show());
})();
