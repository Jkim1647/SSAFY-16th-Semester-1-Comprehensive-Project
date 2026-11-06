<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="board"/>
<!doctype html><html lang="ko"><head><title><c:out value="${board.title}"/> · 게시판</title><%@ include file="../fragments/head.jsp" %></head>
<body><%@ include file="../fragments/header.jsp" %><main class="page-wrap" style="max-width:850px">
<c:if test="${board.hidden}"><p class="alert alert-warning">숨김 처리된 게시글입니다. 관리자에게만 표시됩니다.</p></c:if>
<article class="mb-4"><c:if test="${board.notice}"><span class="badge text-bg-primary">공지</span></c:if>
<h1 class="page-title mt-2"><c:out value="${board.title}"/></h1>
<p class="small text-body-secondary"><c:out value="${board.writerName}"/> · ${fn:replace(fn:substring(board.createdAt,0,16),'T',' ')} · 조회 ${board.viewCount}<c:if test="${board.edited}"> · 수정 ${fn:replace(fn:substring(board.updatedAt,0,16),'T',' ')}</c:if></p>
<div class="post-body"><c:out value="${board.content}"/></div></article>
<section class="d-flex flex-wrap gap-2 mb-3" aria-label="게시글 반응" data-board-id="${board.boardId}">
<c:choose><c:when test="${not empty sessionScope.loginUser}">
<button type="button" class="reaction-icon reaction-like" data-reaction="like" aria-label="좋아요" aria-pressed="${board.liked}"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-8.5-5.2-8.5-11.1C3.5 6.8 5.5 5 8 5c1.8 0 3.1 1 4 2.2C12.9 6 14.2 5 16 5c2.5 0 4.5 1.8 4.5 4.9C20.5 15.8 12 21 12 21z"/></svg></button><span class="align-self-center small" data-like-count aria-label="좋아요 수">${board.likeCount}</span>
<button type="button" class="reaction-icon ms-auto" data-reaction="bookmark" aria-label="북마크" aria-pressed="${board.bookmarked}"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3.5h12v17l-6-4-6 4z"/></svg></button>
</c:when><c:otherwise><a class="reaction-icon reaction-like" href="${root}/member?action=login-form" aria-label="로그인 후 좋아요"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-8.5-5.2-8.5-11.1C3.5 6.8 5.5 5 8 5c1.8 0 3.1 1 4 2.2C12.9 6 14.2 5 16 5c2.5 0 4.5 1.8 4.5 4.9C20.5 15.8 12 21 12 21z"/></svg></a><span class="align-self-center small" aria-label="좋아요 수">${board.likeCount}</span><a class="reaction-icon ms-auto" href="${root}/member?action=login-form" aria-label="로그인 후 북마크"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3.5h12v17l-6-4-6 4z"/></svg></a></c:otherwise></c:choose>
<p class="w-100 small mb-0" id="reaction-message" role="status" aria-live="polite"></p></section>
<div class="d-flex justify-content-end flex-wrap gap-2 mb-4"><a class="btn btn-sm btn-link text-secondary text-decoration-none" href="${root}/board?${state}">목록</a>
<c:if test="${canManage}"><a class="btn btn-outline-primary" href="${root}/board?action=edit-form&amp;id=${board.boardId}&amp;${state}">수정</a>
<form method="post" action="${root}/board?action=delete" data-confirm="이 글과 연결된 댓글·저장 기록을 삭제할까요?"><%@ include file="fields.jspf" %><button class="btn btn-outline-danger">삭제</button></form></c:if></div>
<section class="border-top pt-3" aria-labelledby="comments-title"><h2 class="h5" id="comments-title">댓글 ${fn:length(comments)}개</h2>
<c:forEach var="c" items="${comments}"><article class="border rounded p-3 my-3 ${c.hidden ? 'bg-body-tertiary' : ''}" id="comment-${c.commentId}">
<p class="small"><strong><c:out value="${c.writerName}"/></strong> · ${fn:replace(fn:substring(c.createdAt,0,16),'T',' ')} <c:if test="${c.hidden}"><span class="badge text-bg-secondary">숨김</span></c:if></p>
<p class="post-body"><c:out value="${c.content}"/></p><div class="d-flex flex-wrap gap-2">
<c:if test="${not empty sessionScope.loginUser && (sessionScope.loginUser.admin || sessionScope.loginUser.memberId == c.memberId)}">
<details class="flex-fill"><summary>댓글 수정</summary><form method="post" action="${root}/board?action=comment-edit" class="vstack gap-2 mt-2"><%@ include file="fields.jspf" %><input type="hidden" name="commentId" value="${c.commentId}"><label class="visually-hidden" for="edit-${c.commentId}">댓글 내용</label><textarea id="edit-${c.commentId}" name="content" required maxlength="2000" class="form-control"><c:out value="${c.content}"/></textarea><button class="btn btn-sm btn-outline-primary align-self-start">수정 저장</button></form></details>
<form method="post" action="${root}/board?action=comment-delete" data-confirm="댓글을 삭제할까요?"><%@ include file="fields.jspf" %><input type="hidden" name="commentId" value="${c.commentId}"><button class="btn btn-sm btn-outline-danger">댓글 삭제</button></form></c:if>
</div>
<c:if test="${not empty sessionScope.loginUser}"><details class="mt-2"><summary>댓글 신고</summary><form method="post" action="${root}/board?action=report" class="vstack gap-2 mt-2"><%@ include file="fields.jspf" %><input type="hidden" name="commentId" value="${c.commentId}"><label for="comment-reason-${c.commentId}">신고 사유</label><input id="comment-reason-${c.commentId}" name="reason" required maxlength="500" class="form-control"><button class="btn btn-sm btn-outline-danger align-self-start">신고 접수</button></form></details></c:if>
<c:if test="${sessionScope.loginUser.admin}"><form method="post" action="${root}/board?action=moderate" class="d-flex flex-wrap gap-2 mt-2"><%@ include file="fields.jspf" %><input type="hidden" name="commentId" value="${c.commentId}"><input type="hidden" name="hidden" value="${not c.hidden}"><label for="moderate-${c.commentId}" class="visually-hidden">댓글 관리 사유</label><input id="moderate-${c.commentId}" name="reason" class="form-control" maxlength="500" required placeholder="댓글 관리 사유"><button class="btn btn-sm btn-warning">${c.hidden ? '댓글 복구' : '댓글 숨김'}</button></form></c:if>
</article></c:forEach>
<c:if test="${empty comments}"><p class="text-body-secondary">표시할 댓글이 없습니다.</p></c:if>
<c:url var="commentLoginUrl" value="/member"><c:param name="action" value="login-form"/><c:param name="next" value="/board?action=detail&id=${board.boardId}"/></c:url>
<form method="post" action="${root}/board?action=comment-add" class="vstack gap-2" <c:if test="${empty sessionScope.loginUser}">data-login-url="<c:out value='${commentLoginUrl}'/>" novalidate</c:if>><%@ include file="fields.jspf" %><label for="new-comment">댓글 작성</label><textarea id="new-comment" name="content" required maxlength="2000" rows="3" class="form-control"></textarea><button class="btn btn-sm btn-primary align-self-end">댓글 등록</button></form></section>
<c:if test="${sessionScope.loginUser.admin}"><section class="border-top mt-4 pt-3"><h2 class="h5">관리자 관리</h2>
<form method="post" action="${root}/board?action=moderate" class="vstack gap-2 mb-3"><%@ include file="fields.jspf" %><input type="hidden" name="hidden" value="${not board.hidden}"><label for="moderation-reason">게시글 관리 사유</label><textarea id="moderation-reason" name="reason" required maxlength="500" rows="2" class="form-control"></textarea><button class="btn btn-warning align-self-start">${board.hidden ? '게시글 복구' : '게시글 숨김'}</button></form>
<c:if test="${board.notice}"><form method="post" action="${root}/board?action=pin" class="vstack gap-2 mb-3"><%@ include file="fields.jspf" %>
<label for="pinned">공지 고정</label><select id="pinned" name="pinned" class="form-select"><option value="false" ${not board.pinned ? 'selected' : ''}>고정 해제</option><option value="true" ${board.pinned ? 'selected' : ''}>고정 사용</option></select>
<label for="pin-order">노출 순서 (작을수록 먼저, 0~999)</label><input id="pin-order" name="pinOrder" type="number" min="0" max="999" required value="${board.pinOrder}" class="form-control">
<label for="pin-start">노출 시작 (한국 시간, 비우면 제한 없음)</label><input id="pin-start" name="pinStart" type="datetime-local" value="${board.pinStart}" class="form-control">
<label for="pin-end">노출 종료 (한국 시간, 비우면 제한 없음)</label><input id="pin-end" name="pinEnd" type="datetime-local" value="${board.pinEnd}" class="form-control"><button class="btn btn-outline-primary align-self-start">공지 고정 설정 저장</button></form></c:if>
<details><summary>최근 관리 기록</summary><c:forEach var="h" items="${history}"><p class="small mt-2"><c:out value="${h.createdAt}"/> · <c:out value="${h.adminName}"/> · <c:out value="${h.action}"/> · 댓글 ${h.commentId}<br><c:out value="${h.reason}"/></p></c:forEach></details>
</section></c:if>
</main><%@ include file="../fragments/footer.jsp" %><script src="${root}/assets/js/board.js?v=20261030-2" defer></script></body></html>
