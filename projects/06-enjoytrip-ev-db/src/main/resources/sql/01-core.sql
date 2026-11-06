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
