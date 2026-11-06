package com.ssafy.enjoytrip.model.dto;

import java.util.Set;

/** 목록 검색·페이징 조건 (수업 BE_06 SearchCondition). 검색 key 는 기능마다 허용 목록을 넘겨 SQL 에 그대로 넣지 않는다. */
public class SearchCondition {

    private final Set<String> allowedKeys;
    private String key;
    private String word;
    private int currentPage = 1;
    private int itemsPerPage = 10;

    public SearchCondition(Set<String> allowedKeys, String key, String word, int currentPage, int itemsPerPage) {
        this.allowedKeys = allowedKeys;
        this.key = key;
        this.word = word == null ? null : word.trim();
        this.currentPage = Math.max(1, currentPage);
        this.itemsPerPage = Math.max(1, Math.min(itemsPerPage, 100));
    }

    public int getOffset() {
        return (currentPage - 1) * itemsPerPage;
    }

    public boolean hasKeyword() {
        return normalizedKey() != null && word != null && !word.isBlank();
    }

    /** 허용 목록에 있는 key 만 돌려준다. 없으면 null → 검색 조건 없음. */
    public String normalizedKey() {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = key.trim();
        return allowedKeys.contains(k) ? k : null;
    }

    /** LIKE 검색어: %, _ 를 이스케이프해서 사용자가 와일드카드를 넣지 못하게 한다. */
    public String likeWord() {
        return "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public String getKey() {
        return key;
    }

    public String getWord() {
        return word;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }
}
