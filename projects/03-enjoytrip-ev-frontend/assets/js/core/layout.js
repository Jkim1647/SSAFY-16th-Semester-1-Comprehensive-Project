// 모든 페이지 공통 헤더/푸터. 각 HTML 은 <header id="app-header"> / <footer id="app-footer"> 만 둔다.
import { currentUser, logout } from "./auth.js";
import { escapeHtml } from "./util.js";

const MENUS = [
  { href: "attraction.html", label: "관광지 조회" },
  { href: "plan.html", label: "여행 계획" },
  { href: "hotplace.html", label: "핫플레이스" },
  { href: "board.html?type=notice", label: "공지사항" },
  { href: "board.html?type=free", label: "자유게시판" },
  { href: "developers.html", label: "개발자" },
];

const isActive = (href) => {
  const [page, query] = href.split("?");
  const current = location.pathname.split("/").pop() || "index.html";
  const samePage = current === page || (page === "board.html" && current.startsWith("board"));
  if (!samePage) return false;
  if (!query) return true;
  const type = new URLSearchParams(location.search).get("type") || "free";
  return query === `type=${type}`;
};

const renderHeader = () => {
  const header = document.getElementById("app-header");
  if (!header) return;
  const user = currentUser();

  const authArea = user
    ? `<div class="dropdown">
        <button class="btn btn-sm btn-outline-secondary dropdown-toggle" data-bs-toggle="dropdown" aria-expanded="false">
          ${escapeHtml(user.name)}님
        </button>
        <ul class="dropdown-menu dropdown-menu-end">
          <li><a class="dropdown-item" href="mypage.html">마이페이지</a></li>
          <li><a class="dropdown-item" href="plan.html">내 여행 계획</a></li>
          <li><hr class="dropdown-divider"></li>
          <li><button class="dropdown-item" type="button" id="btn-logout">로그아웃</button></li>
        </ul>
      </div>`
    : `<a class="btn btn-sm btn-link text-body text-decoration-none" href="login.html">로그인</a>
       <a class="btn btn-sm btn-primary" href="signup.html">회원가입</a>`;

  header.className = "site-header border-bottom bg-body sticky-top";
  header.innerHTML = `
    <nav class="navbar navbar-expand-lg container" aria-label="주 메뉴">
      <a class="navbar-brand brand" href="index.html">Enjoy<span>Trip</span></a>
      <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#gnb"
        aria-controls="gnb" aria-expanded="false" aria-label="메뉴 열기">
        <span class="navbar-toggler-icon"></span>
      </button>
      <div class="collapse navbar-collapse" id="gnb">
        <ul class="navbar-nav me-auto">
          ${MENUS.map(
            (m) => `<li class="nav-item"><a class="nav-link${isActive(m.href) ? " active" : ""}"
              ${isActive(m.href) ? 'aria-current="page"' : ""} href="${m.href}">${m.label}</a></li>`
          ).join("")}
        </ul>
        <div class="d-flex align-items-center gap-2 py-2 py-lg-0">${authArea}</div>
      </div>
    </nav>`;

  header.querySelector("#btn-logout")?.addEventListener("click", () => {
    logout();
    location.href = "index.html";
  });
};

const renderFooter = () => {
  const footer = document.getElementById("app-footer");
  if (!footer) return;
  footer.className = "site-footer border-top mt-5";
  footer.innerHTML = `
    <div class="container py-4 small text-body-secondary d-flex flex-column flex-md-row justify-content-between gap-2">
      <span>EnjoyTrip · SSAFY 16기 광주 3반 관통 프로젝트 (Web Front)</span>
      <span>관광 정보: 한국관광공사 국문 관광정보 서비스 · 지도: Kakao Maps</span>
    </div>`;
};

renderHeader();
renderFooter();
