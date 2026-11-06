<%@ page contentType="text/html; charset=UTF-8"%>
<%-- 공통 페이지 번호. 사용 측: <c:set var="pageUrl" value="${root}/board?type=free&key=...&page="/> 후 include. page 는 Page DTO --%>
<c:if test="${page.totalPages > 1}">
  <nav aria-label="페이지 이동" class="mt-3">
    <ul class="pagination pagination-sm justify-content-center">
      <c:if test="${page.hasPre}">
        <li class="page-item"><a class="page-link" href="${pageUrl}${page.startPage - 1}" aria-label="이전">&laquo;</a></li>
      </c:if>
      <c:forEach var="p" begin="${page.startPage}" end="${page.endPage}">
        <li class="page-item ${p == page.condition.currentPage ? 'active' : ''}">
          <a class="page-link" href="${pageUrl}${p}" ${p == page.condition.currentPage ? 'aria-current="page"' : ''}>${p}</a>
        </li>
      </c:forEach>
      <c:if test="${page.hasNext}">
        <li class="page-item"><a class="page-link" href="${pageUrl}${page.endPage + 1}" aria-label="다음">&raquo;</a></li>
      </c:if>
    </ul>
  </nav>
</c:if>
