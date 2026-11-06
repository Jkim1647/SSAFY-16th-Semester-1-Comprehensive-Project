-- 관광지 관련 뉴스 크롤링 (F106)
CREATE TABLE IF NOT EXISTS news (
    news_id      BIGINT       NOT NULL AUTO_INCREMENT,
    keyword      VARCHAR(50)  NOT NULL,          -- 수집에 쓴 검색어
    title        VARCHAR(300) NOT NULL,
    link         VARCHAR(700) NOT NULL,
    source       VARCHAR(100),
    published_at DATETIME,
    crawled_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (news_id),
    UNIQUE KEY uk_news_link (link),
    KEY idx_news_published (published_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
