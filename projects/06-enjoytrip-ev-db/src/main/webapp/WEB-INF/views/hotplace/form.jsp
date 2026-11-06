<%@ page contentType="text/html; charset=UTF-8"%>
<c:set var="menu" value="hotplace" />
<c:set var="isEdit" value="${mode == 'modify'}" />
<!doctype html>
<html lang="ko">
<head>
<title>${isEdit ? '핫플레이스 수정' : '핫플레이스 등록'} · EnjoyTrip EV</title>
<%@ include file="../fragments/head.jsp" %>
<%@ include file="../fragments/leaflet.jspf" %>
<link rel="stylesheet" href="${root}/assets/css/hotplace.css">
</head>
<body>
<%@ include file="../fragments/header.jsp" %>
<main class="page-wrap" style="max-width:1100px">
  <nav aria-label="breadcrumb" class="small mb-2">
    <c:choose>
      <c:when test="${isEdit}"><a href="${root}/hotplace?action=detail&id=${hp.hotplaceId}">← 상세로</a></c:when>
      <c:otherwise><a href="${root}/hotplace">← 핫플레이스 목록</a></c:otherwise>
    </c:choose>
  </nav>
  <h1 class="page-title mb-3">${isEdit ? '핫플레이스 수정' : '핫플레이스 등록'}</h1>
  <c:if test="${not empty error}">
    <div class="alert alert-danger" role="alert"><c:out value="${error}" />
      <c:if test="${not isEdit}"> 사진을 골랐다면 다시 선택해 주세요.</c:if></div>
  </c:if>

  <c:choose>
    <c:when test="${isEdit}">
      <c:set var="formAction" value="${root}/hotplace?action=modify&id=${hp.hotplaceId}" />
      <c:set var="cancelUrl" value="${root}/hotplace?action=detail&id=${hp.hotplaceId}" />
    </c:when>
    <c:otherwise>
      <c:set var="formAction" value="${root}/hotplace?action=regist" />
      <c:set var="cancelUrl" value="${root}/hotplace" />
    </c:otherwise>
  </c:choose>
  <form id="hp-form" method="post" action="${formAction}" enctype="multipart/form-data" class="row g-4" novalidate>
    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
    <input type="hidden" name="latitude" id="hp-lat" value="${hp.latitude}">
    <input type="hidden" name="longitude" id="hp-lng" value="${hp.longitude}">

    <div class="col-lg-6 vstack gap-3">
      <div>
        <label for="hp-title" class="form-label">장소 이름 <span class="text-danger">*</span></label>
        <input id="hp-title" name="title" class="form-control" maxlength="100" required
          value="<c:out value='${hp.title}'/>" placeholder="예: 상무지구 브런치 카페">
      </div>
      <div class="row g-3">
        <div class="col-sm-6">
          <label for="hp-type" class="form-label">장소 유형 <span class="text-danger">*</span></label>
          <select id="hp-type" name="placeType" class="form-select" required>
            <option value="">선택하세요</option>
            <c:forEach var="t" items="${placeTypes}">
              <option value="${t}" ${hp.placeType == t ? 'selected' : ''}>${t}</option>
            </c:forEach>
          </select>
        </div>
        <div class="col-sm-6">
          <label for="hp-date" class="form-label">다녀온 날짜 <span class="text-danger">*</span></label>
          <input id="hp-date" name="visitedOn" type="date" class="form-control" required max="${today}" value="${hp.visitedOn}">
        </div>
      </div>
      <div>
        <label for="hp-photo" class="form-label">사진 <span class="small text-body-secondary">(JPG·PNG·WEBP·GIF, 5MB 이하)</span></label>
        <c:if test="${isEdit and not empty hp.photoFile}">
          <div class="d-flex align-items-center gap-3 mb-2">
            <img class="hp-current" src="${root}/uploads/${fn:escapeXml(hp.photoFile)}" alt="현재 사진">
            <div class="form-check">
              <input class="form-check-input" type="checkbox" name="removePhoto" value="1" id="hp-remove-photo">
              <label class="form-check-label small" for="hp-remove-photo">현재 사진 삭제</label>
            </div>
          </div>
          <p class="pick-hint text-body-secondary mb-1">새 사진을 고르면 현재 사진을 바꿉니다.</p>
        </c:if>
        <input id="hp-photo" name="photo" type="file" class="form-control" accept="image/jpeg,image/png,image/webp,image/gif">
        <div class="invalid-feedback" id="photo-error"></div>
        <p class="pick-hint text-primary mt-1 mb-0" id="exif-hint" aria-live="polite"></p>
        <img id="hp-preview" class="hp-preview img-fluid rounded mt-2 d-none" alt="선택한 사진 미리보기">
      </div>
      <div>
        <label for="hp-desc" class="form-label">설명</label>
        <textarea id="hp-desc" name="description" class="form-control" rows="6" maxlength="2000"
          placeholder="어떤 점이 좋았나요? 충전 팁도 남겨 주세요."><c:out value="${hp.description}" /></textarea>
      </div>
      <div class="d-flex gap-2">
        <button class="btn btn-primary flex-fill" type="submit">${isEdit ? '수정하기' : '등록하기'}</button>
        <a class="btn btn-outline-secondary" href="${cancelUrl}">취소</a>
      </div>
    </div>

    <div class="col-lg-6">
      <span class="form-label d-block">위치 <span class="text-danger">*</span></span>
      <div id="pick-map" class="hp-pick-map" aria-label="위치 선택 지도 (클릭해서 위치 지정)"></div>
      <p class="pick-hint text-body-secondary mt-1 mb-0" id="pick-hint" aria-live="polite">지도를 클릭하면 그 위치가 선택됩니다.</p>
    </div>
  </form>
</main>
<%@ include file="../fragments/footer.jsp" %>
<script src="${root}/assets/js/hotplace.js"></script>
</body>
</html>
