<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="board"/><c:set var="editing" value="${board.boardId > 0}"/>
<!doctype html><html lang="ko"><head><title>${editing ? '글 수정' : '글쓰기'} · 게시판</title><%@ include file="../fragments/head.jsp" %></head>
<body><%@ include file="../fragments/header.jsp" %><main class="page-wrap" style="max-width:800px">
<h1 class="page-title">${editing ? '글 수정' : '글쓰기'}</h1>
<c:if test="${not empty error}"><p class="alert alert-danger" role="alert"><c:out value="${error}"/></p></c:if>
<form method="post" action="${root}/board?action=${editing ? 'modify' : 'write'}" class="vstack gap-3">
<%@ include file="fields.jspf" %>
<c:if test="${not editing}"><div><label for="boardType" class="form-label">글 유형</label><select id="boardType" name="boardType" class="form-select"><option value="FREE">일반 글</option><c:if test="${sessionScope.loginUser.admin}"><option value="NOTICE" ${board.notice ? 'selected' : ''}>공지사항</option></c:if></select></div></c:if>
<c:if test="${editing && board.notice}"><p class="badge text-bg-primary align-self-start">공지사항</p></c:if>
<div><label for="postCategory" class="form-label">분류</label><select id="postCategory" name="postCategory" class="form-select"><c:forEach var="cat" items="${categories}"><option value="${cat.value}" ${board.category == cat.value ? 'selected' : ''}>${cat.label}</option></c:forEach></select></div>
<div><label for="title" class="form-label">제목</label><input id="title" name="title" class="form-control" required maxlength="${titleMax}" value="<c:out value='${board.title}'/>"></div>
<div><label for="content" class="form-label">내용</label><textarea id="content" name="content" rows="14" class="form-control" required maxlength="${contentMax}"><c:out value="${board.content}"/></textarea><p class="form-hint">최대 10,000자. HTML은 일반 글자로 표시됩니다.</p></div>
<div class="d-flex gap-2"><button class="btn btn-primary">${editing ? '수정' : '등록'}</button><a class="btn btn-outline-secondary" href="${root}/board?${state}">목록으로</a><c:if test="${editing}"><a class="btn btn-link" href="${root}/board?action=detail&amp;id=${board.boardId}&amp;${state}">수정 취소</a></c:if></div>
</form></main><%@ include file="../fragments/footer.jsp" %></body></html>
