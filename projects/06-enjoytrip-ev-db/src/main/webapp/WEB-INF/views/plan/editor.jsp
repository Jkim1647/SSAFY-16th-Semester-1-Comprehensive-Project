<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="plan" />
<c:set var="isEdit" value="${plan.planId != 0}" />
<!doctype html>
<html lang="ko">
<head>
<title>${isEdit ? '여행 계획 수정' : '새 여행 계획'} · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<%@ include file="../fragments/leaflet.jspf" %>
<link rel="stylesheet" href="${root}/assets/css/plan.css">
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="app-shell">
  <section class="app-panel" aria-labelledby="panel-title">
    <div class="app-panel__head">
      <div class="d-flex justify-content-between align-items-center mb-2">
        <h1 id="panel-title" class="h5 fw-bold mb-0">${isEdit ? '여행 계획 수정' : '새 여행 계획'}</h1>
        <a class="small" href="${root}/plan<c:if test='${isEdit}'>?action=detail&amp;planId=${plan.planId}</c:if>">취소</a>
      </div>
      <ul class="nav panel-tabs" role="tablist">
        <li class="nav-item" role="presentation">
          <button class="nav-link" id="tab-find" data-bs-toggle="tab" data-bs-target="#pane-find" type="button" role="tab"
            aria-controls="pane-find" aria-selected="false">장소 찾기</button>
        </li>
        <li class="nav-item" role="presentation">
          <button class="nav-link active" id="tab-plan" data-bs-toggle="tab" data-bs-target="#pane-plan" type="button" role="tab"
            aria-controls="pane-plan" aria-selected="true">내 계획 (<span id="stop-count">0</span>)</button>
        </li>
      </ul>
    </div>

    <div class="app-panel__body tab-content">
      <%-- 장소 찾기: /attraction?action=search-json --%>
      <div class="tab-pane fade" id="pane-find" role="tabpanel" aria-labelledby="tab-find" tabindex="0">
        <form id="find-form" class="p-3 vstack gap-2 border-bottom" role="search">
          <div class="input-group">
            <label class="visually-hidden" for="find-keyword">관광지 이름</label>
            <input id="find-keyword" class="form-control" type="search" placeholder="관광지 이름 (예: 죽녹원)" maxlength="30">
            <button class="btn btn-primary" type="submit">검색</button>
          </div>
          <label class="visually-hidden" for="find-type">유형</label>
          <select id="find-type" class="form-select form-select-sm">
            <option value="">모든 유형</option>
            <c:forEach var="t" items="${contentTypes}">
              <option value="${t.code}"><c:out value="${t.name}" /></option>
            </c:forEach>
          </select>
        </form>
        <p id="find-meta" class="result-meta mb-0" aria-live="polite">이름으로 검색해 계획에 담을 장소를 찾으세요.</p>
        <ul id="find-list" class="spot-list"></ul>
        <nav id="find-pages" aria-label="장소 검색 결과 페이지" hidden>
          <div class="d-flex justify-content-center align-items-center gap-2 p-3">
            <button id="find-prev" type="button" class="btn btn-sm btn-outline-secondary">이전</button>
            <span id="find-page-label" aria-live="polite"></span>
            <button id="find-next" type="button" class="btn btn-sm btn-outline-secondary">다음</button>
          </div>
        </nav>
      </div>

      <%-- 내 계획 --%>
      <div class="tab-pane fade show active" id="pane-plan" role="tabpanel" aria-labelledby="tab-plan" tabindex="0">
        <form id="meta-form" class="p-3 vstack gap-2 border-bottom" novalidate>
          <div>
            <label for="plan-title" class="form-label small mb-1">여행 제목 <span class="text-danger" aria-hidden="true">*</span></label>
            <input id="plan-title" class="form-control" maxlength="100" required placeholder="예: 광주 → 담양 1박 2일 드라이브">
          </div>
          <div class="row g-2">
            <div class="col-6">
              <label for="plan-start" class="form-label small mb-1">시작일</label>
              <input id="plan-start" type="date" class="form-control form-control-sm">
            </div>
            <div class="col-6">
              <label for="plan-end" class="form-label small mb-1">종료일</label>
              <input id="plan-end" type="date" class="form-control form-control-sm">
            </div>
          </div>
          <div class="row g-2 align-items-end">
            <div class="col-6">
              <label for="plan-soc" class="form-label small mb-1">출발 배터리 (%)</label>
              <input id="plan-soc" type="number" class="form-control form-control-sm" min="10" max="100" step="5">
            </div>
            <div class="col-6">
              <div class="form-check mb-1">
                <input class="form-check-input" type="checkbox" id="plan-return">
                <label class="form-check-label small" for="plan-return">출발지로 돌아오기</label>
              </div>
            </div>
          </div>
          <div>
            <label for="plan-memo" class="form-label small mb-1">상세 계획 · 메모</label>
            <textarea id="plan-memo" class="form-control form-control-sm" rows="2" maxlength="1000" placeholder="숙소, 준비물, 일정 메모 등"></textarea>
          </div>
        </form>

        <p class="small text-body-secondary px-3 pt-2 mb-0" id="dnd-hint">⋮⋮ 손잡이를 끌거나, 손잡이에 초점을 두고 Alt + ↑/↓ 로 순서를 바꿀 수 있습니다. 첫 번째 장소가 출발지입니다.</p>
        <ol id="stop-list" class="stop-list" aria-describedby="dnd-hint"></ol>

        <div class="plan-summary">
          <div class="d-flex flex-wrap gap-2 mb-2">
            <button id="btn-optimize" class="btn btn-sm btn-outline-primary" type="button">방문 순서 최적화</button>
            <button id="btn-ev" class="btn btn-sm btn-outline-warning text-body" type="button">⚡ 충전 경로 계산</button>
          </div>
          <div id="route-result" class="small mb-2" aria-live="polite"></div>
          <div id="ev-result" class="small mb-2" aria-live="polite"></div>
          <p class="fw-bold mb-2">예상 경비 합계 <span id="sum-cost">0</span>원</p>
          <div id="editor-msg" class="alert alert-danger py-2 small d-none" role="alert"></div>
          <button id="btn-save" class="btn btn-primary w-100" type="button">${isEdit ? '수정 내용 저장' : '계획 저장'}</button>
        </div>
      </div>
    </div>
  </section>

  <section class="app-map" aria-label="지도">
    <div class="map-legend">
      <span class="legend-stop" aria-hidden="true">1</span> 방문지
      <span class="ev-dot ms-2" aria-hidden="true">⚡</span> 충전소
    </div>
    <div id="map" class="map"></div>
  </section>
</main>
<script type="application/json" id="plan-data">${planJson}</script>
<%@ include file="../fragments/scripts.jspf" %>
<script>window.PLAN_MAX_STOPS = ${maxStops};</script>
<script src="${root}/assets/js/plan-editor.js"></script>
</body>
</html>
