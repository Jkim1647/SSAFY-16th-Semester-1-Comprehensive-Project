package com.ssafy.enjoytrip.model.dto;

import java.util.List;

/** 페이징 결과 (수업 BE_06 Page). JSP 는 startPage~endPage 로 페이지 번호를 그린다. */
public class Page<T> {

    private static final int NAV_SIZE = 5;

    private final SearchCondition condition;
    private final int totalItems;
    private final List<T> list;
    private final int totalPages;
    private final int startPage;
    private final int endPage;
    private final boolean hasPre;
    private final boolean hasNext;

    public Page(SearchCondition condition, int totalItems, List<T> list) {
        this.condition = condition;
        this.totalItems = totalItems;
        this.list = list;
        this.totalPages = Math.max(1, (int) Math.ceil((double) totalItems / condition.getItemsPerPage()));
        this.startPage = (condition.getCurrentPage() - 1) / NAV_SIZE * NAV_SIZE + 1;
        this.endPage = Math.min(startPage + NAV_SIZE - 1, totalPages);
        this.hasPre = startPage != 1;
        this.hasNext = endPage < totalPages;
    }

    public SearchCondition getCondition() {
        return condition;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public List<T> getList() {
        return list;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getStartPage() {
        return startPage;
    }

    public int getEndPage() {
        return endPage;
    }

    public boolean isHasPre() {
        return hasPre;
    }

    public boolean isHasNext() {
        return hasNext;
    }
}
