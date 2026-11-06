<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="board"/>
<!doctype html><html lang="ko"><head><title>게시판 신고 관리</title><%@ include file="../fragments/head.jsp" %></head><body><%@ include file="../fragments/header.jsp" %><main class="page-wrap" style="max-width:900px">
<h1 class="page-title">신고 관리</h1><a href="${root}/board">게시판으로</a><p class="text-body-secondary">미처리 신고를 먼저 표시합니다. 숨김·복구는 대상 게시글에서 사유와 함께 처리하세요.</p>
<c:forEach var="r" items="${page.list}"><article class="border rounded p-3 mb-3"><h2 class="h6"><a href="${root}/board?action=detail&amp;id=${r.boardId}#comment-${r.commentId}"><c:out value="${r.title}"/></a></h2><p class="small">신고 #${r.reportId} · <c:out value="${r.reporter}"/> · <c:out value="${r.createdAt}"/> · ${r.status == 'OPEN' ? '미처리' : '처리 완료'}<c:if test="${r.commentId > 0}"> · 댓글 #${r.commentId}</c:if></p>
<c:if test="${r.commentId > 0}"><p class="post-body"><c:out value="${r.reportedContent}"/></p></c:if><p class="post-body">신고 사유: <c:out value="${r.reason}"/></p>
<c:choose><c:when test="${r.status == 'OPEN'}"><form method="post" action="${root}/board?action=resolve-report" class="vstack gap-2"><input type="hidden" name="_csrf" value="${sessionScope.csrfToken}"><input type="hidden" name="reportId" value="${r.reportId}"><input type="hidden" name="page" value="${page.condition.currentPage}"><label for="resolution-${r.reportId}">처리 사유</label><input id="resolution-${r.reportId}" name="reason" required maxlength="500" class="form-control"><button class="btn btn-outline-primary align-self-start">처리 완료</button></form></c:when><c:otherwise><p>처리 사유: <c:out value="${r.resolution}"/></p></c:otherwise></c:choose></article></c:forEach>
<c:if test="${empty page.list}"><p class="empty-state">신고가 없습니다.</p></c:if>
<c:set var="pageUrl" value="${root}/board?action=reports&amp;page="/><%@ include file="../fragments/pagination.jsp" %>
</main><%@ include file="../fragments/footer.jsp" %></body></html>
