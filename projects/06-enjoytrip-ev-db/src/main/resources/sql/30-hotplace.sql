-- 회원 핫플레이스 (F105)
CREATE TABLE IF NOT EXISTS hotplaces (
    hotplace_id BIGINT         NOT NULL AUTO_INCREMENT,
    member_id   BIGINT         NOT NULL,
    title       VARCHAR(100)   NOT NULL,
    place_type  VARCHAR(20)    NOT NULL,         -- 관광지, 음식점, 카페, 충전소, 기타
    visited_on  DATE           NOT NULL,
    description VARCHAR(2000),
    latitude    DECIMAL(13, 10) NOT NULL,
    longitude   DECIMAL(13, 10) NOT NULL,
    photo_file  VARCHAR(100),                    -- 업로드 폴더 안의 파일명 (서버가 만든 UUID 이름)
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (hotplace_id),
    KEY idx_hotplaces_member (member_id),
    CONSTRAINT fk_hotplaces_member FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
