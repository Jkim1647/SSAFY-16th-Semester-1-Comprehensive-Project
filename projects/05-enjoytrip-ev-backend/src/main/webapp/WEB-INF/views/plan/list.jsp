<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="plan" />
<!doctype html>
<html lang="ko">
<head>
<title>내 여행 계획 · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<link rel="stylesheet" href="${root}/assets/css/plan.css">
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="page-wrap">
  <div class="d-flex flex-wrap justify-content-between align-items-end gap-2 mb-3">
    <div>
      <h1 class="page-title mb-1">내 여행 계획</h1>
      <p class="text-body-secondary small mb-0">관광지를 담고 순서를 정하면 전기차 충전이 필요한 구간과 충전소를 함께 계산합니다.</p>
    </div>
    <a class="btn btn-primary" href="${root}/plan?action=create-form">새 계획 만들기</a>
  </div>

  <c:choose>
    <c:when test="${empty plans}">
      <div class="empty-state border rounded-3">
        아직 만든 여행 계획이 없습니다.<br>
        <a class="btn btn-sm btn-outline-primary mt-2" href="${root}/plan?action=create-form">첫 계획 만들기</a>
        <a class="btn btn-sm btn-link mt-2" href="${root}/attraction">관광지 둘러보기</a>
      </div>
    </c:when>
    <c:otherwise>
      <div class="table-responsive">
        <table class="table board-table align-middle">
          <caption class="visually-hidden">내 여행 계획 목록</caption>
          <thead>
            <tr><th scope="col">제목</th><th scope="col">여행 기간</th><th scope="col" class="text-end">관광지</th>
              <th scope="col" class="text-end">예상 경비</th><th scope="col">수정일</th><th scope="col"><span class="visually-hidden">관리</span></th></tr>
          </thead>
          <tbody>
            <c:forEach var="p" items="${plans}">
              <tr>
                <td class="col-title"><a href="${root}/plan?action=detail&planId=${p.planId}"><c:out value="${p.title}" /></a></td>
                <td class="small text-nowrap">
                  <c:choose>
                    <c:when test="${empty p.startDate}"><span class="text-body-secondary">미정</span></c:when>
                    <c:otherwise>${p.startDate}<c:if test="${not empty p.endDate and p.endDate != p.startDate}"> ~ ${p.endDate}</c:if>
                      <c:if test="${not empty p.days}"> <span class="text-body-secondary">(${p.days}일)</span></c:if></c:otherwise>
                  </c:choose>
                </td>
                <td class="text-end">${p.stopCount}곳</td>
                <td class="text-end text-nowrap"><fmt:formatNumber value="${p.totalCost}" />원</td>
                <td class="small text-body-secondary text-nowrap">${p.updatedLabel}</td>
                <td class="text-end text-nowrap">
                  <a class="btn btn-sm btn-outline-secondary" href="${root}/plan?action=edit-form&planId=${p.planId}">수정</a>
                  <form method="post" action="${root}/plan?action=delete" class="d-inline"
                    onsubmit="return confirm('이 여행 계획을 삭제할까요? 되돌릴 수 없습니다.')">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                    <input type="hidden" name="planId" value="${p.planId}">
                    <button class="btn btn-sm btn-outline-danger" type="submit">삭제</button>
                  </form>
                </td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
    </c:otherwise>
  </c:choose>
</main>
<%@ include file="../fragments/footer.jsp" %>
</body>
</html>
