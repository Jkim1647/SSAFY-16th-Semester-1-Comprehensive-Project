-- EnjoyTrip DB Schema (DDL)
-- 생성 원본: src/main/resources/sql/*.sql (앱이 시작할 때 실행하는 것과 같은 DDL, 같은 순서)
-- 데이터는 넣지 않는다. 데이터는 앱 첫 실행 때 자동 적재되거나 enjoytrip_init.sql 로 넣는다.
-- 실행: mysql -u ssafy -p < enjoytrip_schema.sql

CREATE DATABASE IF NOT EXISTS enjoytrip DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE enjoytrip;

-- ============================================================
-- 01-core.sql
-- ============================================================
-- 회원 · 관광지 (F101~F103, F107, F108)
CREATE TABLE IF NOT EXISTS members (
    member_id     BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(100) NOT NULL,
    name          VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(10)  NOT NULL DEFAULT 'USER',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    UNIQUE KEY uk_members_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 비밀번호 재설정 토큰: 원문은 링크로만 전달하고 DB 에는 SHA-256 해시만 둔다. 15분 안에 한 번만 쓸 수 있다.
CREATE TABLE IF NOT EXISTS password_resets (
    token_hash CHAR(64)  NOT NULL,
    member_id  BIGINT    NOT NULL,
    expires_at DATETIME  NOT NULL,
    used_at    DATETIME,
    created_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token_hash),
    KEY idx_password_resets_member (member_id),
    CONSTRAINT fk_password_resets_member FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS content_types (
    content_type_id INT         NOT NULL,
    name            VARCHAR(20) NOT NULL,
    PRIMARY KEY (content_type_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 대용량 기준 데이터를 이미 적재했는지 표시한다 (재시작할 때 다시 넣지 않으려고)
CREATE TABLE IF NOT EXISTS data_seed_state (
    seed_key   VARCHAR(40) NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (seed_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS signgu (
    sido_code   INT         NOT NULL,
    signgu_code INT         NOT NULL,
    name        VARCHAR(30) NOT NULL,
    PRIMARY KEY (sido_code, signgu_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 시도 / 구군 (싸피 제공 sidos / guguns 구조). 구군 코드는 시도마다 1부터 다시 시작하므로 (sido_code, gugun_code) 로 식별한다.
CREATE TABLE IF NOT EXISTS sidos (
    `no`      INT         NOT NULL AUTO_INCREMENT,
    sido_code INT         NOT NULL,
    sido_name VARCHAR(20),
    PRIMARY KEY (`no`),
    UNIQUE KEY uk_sidos_code (sido_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS guguns (
    `no`       INT         NOT NULL AUTO_INCREMENT,
    sido_code  INT         NOT NULL,
    gugun_code INT         NOT NULL,
    gugun_name VARCHAR(20),
    PRIMARY KEY (`no`),
    UNIQUE KEY uk_guguns_sido_gugun (sido_code, gugun_code),
    KEY idx_guguns_gugun (gugun_code),
    CONSTRAINT fk_guguns_sido FOREIGN KEY (sido_code) REFERENCES sidos (sido_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 대리키 no 가 PK, content_id 는 UNIQUE (plan_stops 가 content_id 를 참조한다)
CREATE TABLE IF NOT EXISTS attractions (
    `no`            INT            NOT NULL AUTO_INCREMENT,
    content_id      INT            NOT NULL,
    content_type_id INT            NOT NULL,
    title           VARCHAR(200)   NOT NULL,
    addr1           VARCHAR(200),
    addr2           VARCHAR(100),
    tel             VARCHAR(100),
    first_image     VARCHAR(300),
    first_image2    VARCHAR(300),
    latitude        DECIMAL(20, 17) NOT NULL,
    longitude       DECIMAL(20, 17) NOT NULL,
    sido_code       INT,
    signgu_code     INT,
    lcls1           VARCHAR(10),
    lcls2           VARCHAR(10),
    lcls3           VARCHAR(10),
    overview        VARCHAR(10000),
    homepage        VARCHAR(1000),
    map_level       INT,
    PRIMARY KEY (`no`),
    UNIQUE KEY uk_attractions_content (content_id),
    -- 조회 패턴(목록 정렬 = 사진 있는 곳 먼저, 이름순)에 맞춘 인덱스. README 1-5 의 EXPLAIN 결과 참고
    KEY idx_attractions_region_type (sido_code, signgu_code, content_type_id),
    KEY idx_attractions_type_list (content_type_id, ((first_image IS NULL OR first_image = '')), title, content_id),
    KEY idx_attractions_list (((first_image IS NULL OR first_image = '')), title, content_id),
    CONSTRAINT fk_attractions_type FOREIGN KEY (content_type_id) REFERENCES content_types (content_type_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ============================================================
-- 10-ev.sql
-- ============================================================
-- 충전소 예약 + 몰림 시뮬레이션 (F114 확장, F115)
CREATE TABLE IF NOT EXISTS ev_stations (
    stat_id       VARCHAR(20)    NOT NULL,
    name          VARCHAR(100)   NOT NULL,
    latitude      DECIMAL(13, 10) NOT NULL,
    longitude     DECIMAL(13, 10) NOT NULL,
    max_kw        INT            NOT NULL,
    charger_count INT            NOT NULL,
    PRIMARY KEY (stat_id),
    KEY idx_ev_stations_kw (max_kw)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 시뮬레이션 실행 기록. run_id = 0 은 실제 회원 예약에 쓰는 예약어라 AUTO_INCREMENT 는 1 부터.
CREATE TABLE IF NOT EXISTS sim_runs (
    run_id      BIGINT      NOT NULL AUTO_INCREMENT,
    mode        VARCHAR(10) NOT NULL,           -- LOCKED | NAIVE
    queue_type  VARCHAR(10) NOT NULL,           -- memory | kafka
    vehicles    INT         NOT NULL,
    stations    INT         NOT NULL,
    status      VARCHAR(10) NOT NULL,           -- RUNNING | DONE | FAILED
    confirmed   INT         NOT NULL DEFAULT 0,
    rejected    INT         NOT NULL DEFAULT 0,
    overbooked  INT         NOT NULL DEFAULT 0,
    started_at  DATETIME(3) NOT NULL,
    finished_at DATETIME(3),
    PRIMARY KEY (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 충전소 x 1시간 슬롯의 좌석 수. capacity = 충전기 대수, booked = 확정된 예약 수.
-- LOCKED 모드는 이 행을 SELECT ... FOR UPDATE 로 잠근 뒤 booked < capacity 일 때만 예약한다.
CREATE TABLE IF NOT EXISTS ev_slots (
    run_id     BIGINT      NOT NULL DEFAULT 0,
    stat_id    VARCHAR(20) NOT NULL,
    slot_start DATETIME    NOT NULL,
    capacity   INT         NOT NULL,
    booked     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (run_id, stat_id, slot_start),
    CONSTRAINT fk_ev_slots_station FOREIGN KEY (stat_id) REFERENCES ev_stations (stat_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS ev_reservations (
    reservation_id BIGINT      NOT NULL AUTO_INCREMENT,
    run_id         BIGINT      NOT NULL DEFAULT 0,
    stat_id        VARCHAR(20) NOT NULL,
    slot_start     DATETIME    NOT NULL,
    charger_no     INT         NOT NULL,
    member_id      BIGINT,
    vehicle_no     INT,
    status         VARCHAR(10) NOT NULL DEFAULT 'CONFIRMED',   -- CONFIRMED | CANCELED
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (reservation_id),
    KEY idx_ev_res_slot (run_id, stat_id, slot_start),
    KEY idx_ev_res_member (member_id),
    CONSTRAINT fk_ev_res_station FOREIGN KEY (stat_id) REFERENCES ev_stations (stat_id),
    CONSTRAINT fk_ev_res_member FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ============================================================
-- 20-plan.sql
-- ============================================================
-- 여행 계획 (F104)
CREATE TABLE IF NOT EXISTS plans (
    plan_id    BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    title      VARCHAR(100) NOT NULL,
    start_date DATE,
    end_date   DATE,
    start_soc  INT          NOT NULL DEFAULT 80,     -- 출발 배터리(%)
    memo       VARCHAR(1000),
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (plan_id),
    KEY idx_plans_member (member_id),
    CONSTRAINT fk_plans_member FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS plan_stops (
    plan_id    BIGINT NOT NULL,
    seq        INT    NOT NULL,                 -- 방문 순서 (1부터)
    content_id INT    NOT NULL,
    visit_date DATE,
    cost       INT    NOT NULL DEFAULT 0,       -- 예상 경비(원)
    memo       VARCHAR(300),
    PRIMARY KEY (plan_id, seq),
    CONSTRAINT fk_plan_stops_plan FOREIGN KEY (plan_id) REFERENCES plans (plan_id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_stops_attraction FOREIGN KEY (content_id) REFERENCES attractions (content_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ============================================================
-- 30-hotplace.sql
-- ============================================================
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

-- ============================================================
-- 40-board.sql
-- ============================================================
-- 공지사항(F109) · 자유게시판(F110). board_type 으로 구분한다.
CREATE TABLE IF NOT EXISTS boards (
    board_id   BIGINT       NOT NULL AUTO_INCREMENT,
    board_type VARCHAR(10)  NOT NULL,           -- NOTICE | FREE
    member_id  BIGINT       NOT NULL,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    view_count INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (board_id),
    KEY idx_boards_type (board_type, board_id),
    CONSTRAINT fk_boards_member FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ============================================================
-- 45-board-community.sql
-- ============================================================
-- Existing boards rows need no migration. Missing settings mean GENERAL and visible.
CREATE TABLE IF NOT EXISTS board_settings (
 board_id BIGINT NOT NULL PRIMARY KEY,
 category VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
 hidden BOOLEAN NOT NULL DEFAULT FALSE,
 pinned BOOLEAN NOT NULL DEFAULT FALSE,
 pin_order INT NOT NULL DEFAULT 0,
 pin_start DATETIME NULL,
 pin_end DATETIME NULL,
 KEY idx_board_settings_category (category, board_id),
 KEY idx_board_settings_pinned (pinned, pin_order, board_id),
 FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS board_likes (
 board_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (board_id, member_id),
 KEY idx_board_likes_member (member_id, created_at, board_id),
 FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
 FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS board_bookmarks (
 board_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (board_id, member_id),
 KEY idx_board_bookmarks_member (member_id, created_at, board_id),
 FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
 FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS board_comments (
 comment_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 board_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 content TEXT NOT NULL, hidden BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 KEY idx_board_comments_board (board_id, comment_id),
 KEY idx_board_comments_member (member_id, comment_id),
 FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
 FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS board_reports (
 report_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 board_id BIGINT NOT NULL, comment_id BIGINT NULL,
 member_id BIGINT NOT NULL, target_key VARCHAR(40) NOT NULL,
 reason VARCHAR(500) NOT NULL, status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
 resolution VARCHAR(500) NULL, resolved_by BIGINT NULL,
 resolved_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_board_report (member_id, target_key),
 KEY idx_board_reports_status (status, report_id),
 FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
 FOREIGN KEY (comment_id) REFERENCES board_comments(comment_id) ON DELETE CASCADE,
 FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE CASCADE,
 FOREIGN KEY (resolved_by) REFERENCES members(member_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS board_moderation (
 moderation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 board_id BIGINT NOT NULL, comment_id BIGINT NULL, admin_id BIGINT NULL,
 action VARCHAR(20) NOT NULL, reason VARCHAR(500) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_board_moderation_board (board_id, moderation_id),
 FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
 FOREIGN KEY (comment_id) REFERENCES board_comments(comment_id) ON DELETE CASCADE,
 FOREIGN KEY (admin_id) REFERENCES members(member_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Records that initial sample setup has already been considered.
CREATE TABLE IF NOT EXISTS board_seed_state (
 seed_key VARCHAR(40) NOT NULL PRIMARY KEY,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 50-news.sql
-- ============================================================
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
