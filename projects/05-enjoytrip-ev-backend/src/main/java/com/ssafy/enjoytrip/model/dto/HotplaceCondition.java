package com.ssafy.enjoytrip.model.dto;

/** 핫플레이스 목록 조건: 장소 유형 · 작성자(내 핫플만) + 페이지. 값이 null 이면 그 조건은 쓰지 않는다. */
public class HotplaceCondition {

    private final String placeType;
    private final Long memberId;
    private final int currentPage;
    private final int itemsPerPage;

    public HotplaceCondition(String placeType, Long memberId, int currentPage, int itemsPerPage) {
        this.placeType = placeType == null || placeType.isBlank() ? null : placeType.trim();
        this.memberId = memberId;
        this.currentPage = Math.max(1, currentPage);
        this.itemsPerPage = Math.max(1, Math.min(itemsPerPage, 500));
    }

    public String getPlaceType() {
        return placeType;
    }

    public Long getMemberId() {
        return memberId;
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
