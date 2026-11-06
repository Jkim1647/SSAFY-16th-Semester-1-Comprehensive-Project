<%@ page contentType="text/html; charset=UTF-8"%>
<%-- 공통 헤더. 사용 측에서 <c:set var="menu" value="attraction"/> 처럼 현재 메뉴를 지정하면 강조된다 --%>
<header class="site-header border-bottom bg-body sticky-top">
  <nav class="navbar navbar-expand-lg container-fluid px-3 px-lg-4" aria-label="주 메뉴">
    <a class="navbar-brand brand" href="${root}/">Enjoy<span>Trip</span> <small class="ev-tag">EV</small></a>
    <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#gnb"
      aria-controls="gnb" aria-expanded="false" aria-label="메뉴 열기">
      <span class="navbar-toggler-icon"></span>
    </button>
    <div class="collapse navbar-collapse" id="gnb">
      <ul class="navbar-nav me-auto">
        <li class="nav-item"><a class="nav-link ${menu == 'attraction' ? 'active' : ''}" href="${root}/attraction">관광지</a></li>
        <li class="nav-item"><a class="nav-link ${menu == 'ev' ? 'active' : ''}" href="${root}/ev">충전 예약</a></li>
        <li class="nav-item"><a class="nav-link ${menu == 'sim' ? 'active' : ''}" href="${root}/sim">예약 몰림 시뮬레이션</a></li>
        <li class="nav-item"><a class="nav-link ${menu == 'plan' ? 'active' : ''}" href="${root}/plan">여행 계획</a></li>
        <li class="nav-item"><a class="nav-link ${menu == 'hotplace' ? 'active' : ''}" href="${root}/hotplace">핫플레이스</a></li>
        <li class="nav-item"><a class="nav-link ${menu == 'news' ? 'active' : ''}" href="${root}/news">여행 뉴스</a></li>
        <li class="nav-item"><a class="nav-link ${menu == 'board' ? 'active' : ''}" href="${root}/board">게시판</a></li>
      </ul>
      <div class="d-flex align-items-center gap-2 py-2 py-lg-0">
        <c:choose>
          <c:when test="${empty sessionScope.loginUser}">
            <a class="btn btn-sm btn-link text-body text-decoration-none" href="${root}/member?action=login-form">로그인</a>
            <a class="btn btn-sm btn-primary" href="${root}/member?action=regist-form">회원가입</a>
          </c:when>
          <c:otherwise>
            <div class="dropdown">
              <button class="btn btn-sm btn-outline-secondary dropdown-toggle" data-bs-toggle="dropdown" aria-expanded="false">
                <c:out value="${sessionScope.loginUser.name}" />님
              </button>
              <ul class="dropdown-menu dropdown-menu-end">
                <li><a class="dropdown-item" href="${root}/member?action=mypage">마이페이지</a></li>
                <li><a class="dropdown-item" href="${root}/plan">내 여행 계획</a></li>
                <li><a class="dropdown-item" href="${root}/board?activity=bookmarks">내 북마크·게시판 활동</a></li>
                <c:if test="${sessionScope.loginUser.admin}">
                  <li><a class="dropdown-item" href="${root}/member?action=list">회원 관리</a></li>
                  <li><a class="dropdown-item" href="${root}/news?action=admin">뉴스 수집</a></li>
                </c:if>
                <li><hr class="dropdown-divider"></li>
                <li>
                  <form method="post" action="${root}/member?action=logout">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                    <button class="dropdown-item" type="submit">로그아웃</button>
                  </form>
                </li>
              </ul>
            </div>
          </c:otherwise>
        </c:choose>
      </div>
    </div>
  </nav>
</header>
<c:if test="${not empty sessionScope.alertMsg}">
  <div class="toast-container position-fixed top-0 end-0 p-3" style="z-index:1080">
    <div class="toast align-items-center text-bg-dark border-0 show" role="status" aria-live="polite" data-bs-delay="4000" id="flash-toast">
      <div class="d-flex">
        <div class="toast-body"><c:out value="${sessionScope.alertMsg}" /></div>
        <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="닫기"></button>
      </div>
    </div>
  </div>
  <c:remove var="alertMsg" scope="session" />
</c:if>
