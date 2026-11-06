<%@ page contentType="text/html; charset=UTF-8"%>
<!doctype html>
<html lang="ko">
<head>
<title>마이페이지 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="page-wrap">
  <h1 class="page-title mb-4">마이페이지</h1>
  <a class="btn btn-outline-primary mb-4" href="${root}/board?activity=bookmarks">내 북마크·게시판 활동</a>

  <section class="mb-5" aria-labelledby="res-title">
    <h2 class="h5 fw-bold" id="res-title">내 충전 예약</h2>
    <c:choose>
      <c:when test="${empty reservations}">
        <p class="text-body-secondary">아직 예약이 없습니다. <a href="${root}/ev">충전소 예약하러 가기</a></p>
      </c:when>
      <c:otherwise>
        <div class="table-responsive">
          <table class="table table-sm board-table">
            <thead><tr><th>충전소</th><th>시간</th><th>충전기</th><th>상태</th><th></th></tr></thead>
            <tbody>
              <c:forEach var="r" items="${reservations}">
                <tr>
                  <td><a href="${root}/ev?action=station&statId=${fn:escapeXml(r.statId)}"><c:out value="${r.stationName}" /></a></td>
                  <td>${r.slotLabel}</td>
                  <td>${r.chargerNo}번</td>
                  <td>
                    <span class="badge ${r.status == 'CONFIRMED' ? 'text-bg-success' : 'text-bg-secondary'}">${r.status == 'CONFIRMED' ? '확정' : '취소'}</span>
                  </td>
                  <td class="text-end">
                    <c:if test="${r.status == 'CONFIRMED'}">
                      <form method="post" action="${root}/ev?action=cancel" onsubmit="return confirm('예약을 취소할까요?')">
                        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                        <input type="hidden" name="reservationId" value="${r.reservationId}">
                        <button class="btn btn-sm btn-outline-danger" type="submit">취소</button>
                      </form>
                    </c:if>
                  </td>
                </tr>
              </c:forEach>
            </tbody>
          </table>
        </div>
      </c:otherwise>
    </c:choose>
  </section>

  <div class="row g-4">
    <section class="col-md-7" aria-labelledby="info-title">
      <h2 class="h5 fw-bold" id="info-title">회원 정보 수정</h2>
      <form method="post" action="${root}/member?action=modify" class="vstack gap-3">
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <div>
          <label class="form-label" for="email">이메일</label>
          <input class="form-control" id="email" value="<c:out value='${member.email}'/>" disabled>
        </div>
        <div>
          <label class="form-label" for="name">이름</label>
          <input class="form-control" id="name" name="name" value="<c:out value='${member.name}'/>" required maxlength="30">
        </div>
        <fieldset class="border rounded p-3">
          <legend class="float-none w-auto px-1 fs-6 mb-0">비밀번호 변경 (선택)</legend>
          <label class="form-label mt-2" for="currentPass">현재 비밀번호</label>
          <input class="form-control" type="password" id="currentPass" name="currentPass" autocomplete="current-password">
          <label class="form-label mt-2" for="newPass">새 비밀번호</label>
          <input class="form-control" type="password" id="newPass" name="newPass" minlength="8" maxlength="64" autocomplete="new-password">
          <p class="form-hint mt-1 mb-0">8~64자, 영문과 숫자를 모두 넣어 주세요.</p>
        </fieldset>
        <button class="btn btn-primary" type="submit">저장</button>
      </form>
      <p class="small text-body-secondary mt-2">가입일 ${fn:substring(member.createdAt, 0, 10)}</p>
    </section>

    <section class="col-md-5" aria-labelledby="out-title">
      <h2 class="h5 fw-bold text-danger" id="out-title">회원 탈퇴</h2>
      <p class="small text-body-secondary">탈퇴하면 여행 계획, 핫플레이스, 게시글, 충전 예약이 모두 삭제됩니다.</p>
      <form method="post" action="${root}/member?action=withdraw" class="vstack gap-2" onsubmit="return confirm('정말 탈퇴할까요? 되돌릴 수 없습니다.')">
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <label class="form-label" for="out-pass">비밀번호 확인</label>
        <input class="form-control" type="password" id="out-pass" name="pass" required autocomplete="current-password">
        <button class="btn btn-outline-danger" type="submit">탈퇴하기</button>
      </form>
    </section>
  </div>
</main>
<%@ include file="../fragments/footer.jsp" %>
</body>
</html>
