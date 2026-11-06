package com.ssafy.enjoytrip.model.dto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Only these normalized values may control SQL and return URLs. */
public class BoardQuery extends SearchCondition {
    public static final Set<String> CATEGORIES = Set.of("GENERAL", "REVIEW", "TIP", "QUESTION", "COMPANION");
    private final String type, category, sort, activity;
    public BoardQuery(String type, String category, String sort, String activity, String key, String word, int page) {
        super(Set.of("title", "content", "writer"), key, truncate(word, 100), Math.min(100000, Math.max(1, page)), 10);
        this.type = Set.of("free", "notice").contains(safe(type)) ? type : "";
        this.category = CATEGORIES.contains(safe(category)) ? category : "";
        this.sort = Set.of("views", "likes").contains(safe(sort)) ? sort : "latest";
        this.activity = Set.of("posts", "comments", "likes", "bookmarks").contains(safe(activity)) ? activity : "";
    }
    private static String safe(String s) {
        return s == null ? "" : s;
    }
    private static String truncate(String s, int max) {
        return s == null ? "" : s.substring(0, Math.min(max, s.length()));
    }
    public String getType() {
        return type;
    }
    public String getCategory() {
        return category;
    }
    public String getSort() {
        return sort;
    }
    public String getActivity() {
        return activity;
    }
    public boolean isPinSection() {
        return type.isEmpty() && category.isEmpty() && activity.isEmpty() && !hasKeyword();
    }
    public String getQueryString() {
        return queryForPage(getCurrentPage());
    }
    public String queryForPage(int page) {
        return "type=" + type + "&category=" + category + "&sort=" + sort + "&activity=" + activity
                + "&key=" + encode(normalizedKey() == null ? "" : normalizedKey()) + "&word=" + encode(getWord())
                + "&page=" + page;
    }
    private static String encode(String s) {
        return URLEncoder.encode(safe(s), StandardCharsets.UTF_8);
    }
}
