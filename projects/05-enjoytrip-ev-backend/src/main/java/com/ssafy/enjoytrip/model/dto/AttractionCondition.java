package com.ssafy.enjoytrip.model.dto;

/** 관광지 검색 조건: 시군구 · 콘텐츠 유형 · 이름 키워드 + 페이지. 값이 null/0 이면 그 조건은 쓰지 않는다. */
public class AttractionCondition {

    private final Integer signguCode;
    private final Integer contentTypeId;
    private final String keyword;
    private final int currentPage;
    private final int itemsPerPage;

    public AttractionCondition(Integer signguCode, Integer contentTypeId, String keyword, int currentPage, int itemsPerPage) {
        this.signguCode = signguCode == null || signguCode <= 0 ? null : signguCode;
        this.contentTypeId = contentTypeId == null || contentTypeId <= 0 ? null : contentTypeId;
        this.keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        this.currentPage = Math.max(1, currentPage);
        this.itemsPerPage = Math.max(1, Math.min(itemsPerPage, 100));
    }

    public Integer getSignguCode() {
        return signguCode;
    }

    public Integer getContentTypeId() {
        return contentTypeId;
    }

    public String getKeyword() {
        return keyword;
    }

    public String likeKeyword() {
        return "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public int getOffset() {
        return (currentPage - 1) * itemsPerPage;
    }

    /** 공통 Page 의 페이지 번호 계산을 재사용하기 위한 변환. */
    public SearchCondition toSearchCondition() {
        return new SearchCondition(java.util.Set.of(), null, null, currentPage, itemsPerPage);
    }
}
