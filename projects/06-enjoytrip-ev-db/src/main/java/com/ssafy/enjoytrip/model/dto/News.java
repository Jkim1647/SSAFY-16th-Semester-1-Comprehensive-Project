package com.ssafy.enjoytrip.model.dto;

import java.time.LocalDateTime;

/** 수집한 관광 관련 뉴스 한 건 (F106). link 는 원문 주소로 테이블에서 UNIQUE. */
public class News {

    private long newsId;
    private String keyword;
    private String title;
    private String link;
    private String source;
    private LocalDateTime publishedAt;
    private LocalDateTime crawledAt;

    public News() {
    }

    public News(String keyword, String title, String link, String source, LocalDateTime publishedAt) {
        this.keyword = keyword;
        this.title = title;
        this.link = link;
        this.source = source;
        this.publishedAt = publishedAt;
    }

    /** http/https 주소만 링크로 그린다 (javascript: 같은 주소가 섞여 들어와도 클릭되지 않게). */
    public boolean isSafeLink() {
        return isHttpUrl(link);
    }

    public static boolean isHttpUrl(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim().toLowerCase();
        return u.startsWith("https://") || u.startsWith("http://");
    }

    public long getNewsId() {
        return newsId;
    }

    public void setNewsId(long newsId) {
        this.newsId = newsId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCrawledAt() {
        return crawledAt;
    }

    public void setCrawledAt(LocalDateTime crawledAt) {
        this.crawledAt = crawledAt;
    }
}
