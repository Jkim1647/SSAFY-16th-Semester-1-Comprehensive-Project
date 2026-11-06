<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="board"/>
<!doctype html><html lang="ko"><head><title>게시판 · EnjoyTrip EV</title><%@ include file="../fragments/head.jsp" %></head>
<body><%@ include file="../fragments/header.jsp" %>
<main class="page-wrap" style="max-width:1100px">
 <div class="d-flex flex-wrap align-items-center gap-2 mb-3"><h1 class="page-title me-auto mb-0">게시판</h1>
 <c:if test="${not empty sessionScope.loginUser}"><a class="btn btn-primary" href="${root}/board?action=write-form&amp;${state}">글쓰기</a></c:if>
 <c:if test="${sessionScope.loginUser.admin}"><a class="btn btn-outline-secondary" href="${root}/board?action=reports">신고 관리</a></c:if></div>
 <p class="text-body-secondary">여행 후기와 충전 팁을 나누고, 공지도 함께 확인하세요.</p>
 <nav class="d-flex flex-wrap gap-2 mb-3" aria-label="내 게시판 활동">
 <a class="btn btn-sm ${empty query.activity ? 'btn-primary' : 'btn-outline-primary'}" href="${root}/board">전체 게시판</a>
 <c:if test="${not empty sessionScope.loginUser}">
 <a class="btn btn-sm ${query.activity == 'posts' ? 'btn-primary' : 'btn-outline-primary'}" href="${root}/board?activity=posts">내 글</a>
 <a class="btn btn-sm ${query.activity == 'comments' ? 'btn-primary' : 'btn-outline-primary'}" href="${root}/board?activity=comments">내 댓글</a>
 <a class="btn btn-sm ${query.activity == 'likes' ? 'btn-primary' : 'btn-outline-primary'}" href="${root}/board?activity=likes">좋아요한 글</a>
 <a class="btn btn-sm ${query.activity == 'bookmarks' ? 'btn-primary' : 'btn-outline-primary'}" href="${root}/board?activity=bookmarks">내 북마크</a></c:if></nav>
 <c:if test="${not empty pinned}"><h2 class="h6">고정 공지</h2><div class="table-responsive"><table class="table board-table"><caption class="visually-hidden">고정 공지 목록</caption><tbody><c:forEach var="b" items="${pinned}"><%@ include file="row.jspf" %></c:forEach></tbody></table></div></c:if>
 <p class="small text-body-secondary">목록 ${page.totalItems}건 <c:if test="${not empty pinned}">· 고정 공지 ${fn:length(pinned)}건</c:if></p>
 <c:choose><c:when test="${activityComments}">
 <c:forEach var="c" items="${page.list}"><article class="border rounded p-3 mb-2"><a href="${root}/board?action=detail&amp;id=${c.boardId}&amp;${state}#comment-${c.commentId}"><c:out value="${c.boardTitle}"/></a><p class="post-body my-2"><c:out value="${c.content}"/></p><small>${fn:replace(fn:substring(c.createdAt,0,16),'T',' ')}</small></article></c:forEach>
 </c:when><c:otherwise><div class="table-responsive"><table class="table board-table table-hover"><caption class="visually-hidden">게시글 목록</caption><thead><tr><th>분류</th><th>제목</th><th>작성자</th><th>작성일</th><th>조회</th><th>좋아요</th></tr></thead><tbody><c:forEach var="b" items="${page.list}"><%@ include file="row.jspf" %></c:forEach></tbody></table></div></c:otherwise></c:choose>
 <c:if test="${empty page.list}"><p class="empty-state">표시할 항목이 없습니다.</p></c:if>
 <nav aria-label="게시판 페이지" class="mt-3"><ul class="pagination pagination-sm justify-content-center flex-wrap">
 <c:forEach var="n" begin="1" end="${page.totalPages}"><li class="page-item ${page.condition.currentPage == n ? 'active' : ''}"><a class="page-link" href="${root}/board?${query.queryForPage(n)}" ${page.condition.currentPage == n ? 'aria-current="page"' : ''}>${n}</a></li></c:forEach>
 </ul></nav>
 <c:if test="${not activityComments}">
 <form method="get" action="${root}/board" class="d-flex justify-content-center flex-wrap gap-2 mt-3 board-search" role="search">
 <input type="hidden" name="activity" value="${query.activity}">
 <input type="hidden" name="type" value="${query.type}">
 <label for="key" class="visually-hidden">검색 항목</label><select id="key" name="key" class="form-select form-select-sm w-auto"><option value="title" ${query.key == 'title' ? 'selected' : ''}>제목</option><option value="content" ${query.key == 'content' ? 'selected' : ''}>내용</option><option value="writer" ${query.key == 'writer' ? 'selected' : ''}>작성자</option></select>
 <label for="word" class="visually-hidden">검색어</label><input id="word" name="word" maxlength="100" class="form-control form-control-sm w-auto" placeholder="검색어" value="<c:out value='${query.word}'/>"><button class="btn btn-sm btn-outline-secondary">검색</button>
 </form></c:if>
</main><%@ include file="../fragments/footer.jsp" %></body></html>
