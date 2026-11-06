<%@ page contentType="text/html; charset=UTF-8"%>
<!doctype html>
<html lang="ko">
<head>
<title>EnjoyTrip EV · 전기차로 떠나는 광주·전남 여행</title>
<%@ include file="fragments/head.jsp" %>
</head>
<body>
<%@ include file="fragments/header.jsp" %>
<main>
  <section class="hero hero-ev py-5">
    <div class="container py-lg-4">
      <div class="row align-items-center g-5">
        <div class="col-lg-7">
          <div class="hero-battery mb-4" aria-hidden="true"><i></i></div>
          <h1 class="display-6 mb-3">배터리 12%.<br>다음 충전소까지 38km.<br><em>자리는 남아 있을까요?</em></h1>
          <p class="lead mb-4">연휴 전날 저녁, 광주로 들어오는 전기차는 당신만이 아닙니다.
            EnjoyTrip EV 는 관광지를 고르고, 충전소를 들러 가는 경로를 짜고, 붐비는 충전기를 먼저 예약해 둡니다.</p>
          <div class="d-flex flex-wrap gap-2">
            <a class="btn btn-lg btn-light" href="${root}/sim">충전 티켓팅 이야기 보기</a>
            <a class="btn btn-lg btn-outline-light" href="${root}/ev">충전소 예약</a>
          </div>
        </div>
        <div class="col-lg-5">
          <form class="quick-search p-4 text-body" method="get" action="${root}/attraction" role="search">
            <h2 class="h6 fw-bold mb-3">어디로 갈까요?</h2>
            <label class="visually-hidden" for="q">관광지 이름</label>
            <input class="form-control mb-3" id="q" name="keyword" placeholder="예: 무등산, 순천만, 여수" maxlength="30">
            <div class="chip-row mb-3">
              <c:forEach var="t" items="${contentTypes}">
                <a class="btn btn-sm btn-outline-secondary rounded-pill text-nowrap" href="${root}/attraction?type=${t.code}"><c:out value="${t.name}" /></a>
              </c:forEach>
            </div>
            <button class="btn btn-primary w-100" type="submit">관광지 <fmt:formatNumber value="${attractionCount}" />곳에서 찾기</button>
          </form>
        </div>
      </div>
    </div>
  </section>

  <section class="container py-5">
    <div class="row g-5">
      <div class="col-lg-6">
        <h2 class="h4 fw-bold mb-3">이렇게 여행합니다</h2>
        <ol class="feature-list">
          <li><span class="num">01</span><div><strong>관광지 고르기</strong><br><span class="text-body-secondary">관광공사 데이터로 지역·유형별 관광지를 찾고, 날씨와 일출·일몰을 확인합니다.</span></div></li>
          <li><span class="num">02</span><div><strong>충전까지 계산한 경로</strong><br><span class="text-body-secondary">방문 순서를 최적화하고(Held-Karp), 배터리가 모자란 구간엔 충전소를 끼워 넣습니다(Dijkstra).</span></div></li>
          <li><span class="num">03</span><div><strong>충전기 먼저 잡기</strong><br><span class="text-body-secondary">붐비는 시간대 충전기를 좌석처럼 예약합니다. 수천 대가 동시에 눌러도 한 자리에 한 대만 확정됩니다.</span></div></li>
          <li><span class="num">04</span><div><strong>다녀와서 나누기</strong><br><span class="text-body-secondary">핫플레이스를 사진과 함께 지도에 남기고, 게시판에서 이야기합니다.</span></div></li>
        </ol>
      </div>
      <div class="col-lg-6">
        <h2 class="h4 fw-bold mb-3">최근 예약 몰림 시뮬레이션</h2>
        <c:choose>
          <c:when test="${empty runs}">
            <p class="text-body-secondary">아직 실행 기록이 없습니다. <a href="${root}/sim">첫 시뮬레이션을 돌려 보세요.</a></p>
          </c:when>
          <c:otherwise>
            <table class="table table-sm board-table">
              <thead><tr><th>#</th><th>방식</th><th>차량</th><th>확정</th><th>오버부킹</th></tr></thead>
              <tbody>
                <c:forEach var="r" items="${runs}">
                  <tr>
                    <td>${r.runId}</td>
                    <td>${r.mode == 'LOCKED' ? '잠금 예약' : '잠금 없음'} · <c:out value="${r.queueType}" /></td>
                    <td><fmt:formatNumber value="${r.vehicles}" /></td>
                    <td><fmt:formatNumber value="${r.confirmed}" /></td>
                    <td class="${r.overbooked > 0 ? 'text-danger fw-bold' : 'text-success fw-bold'}">${r.overbooked}</td>
                  </tr>
                </c:forEach>
              </tbody>
            </table>
          </c:otherwise>
        </c:choose>
      </div>
    </div>
  </section>
</main>
<%@ include file="fragments/footer.jsp" %>
</body>
</html>
