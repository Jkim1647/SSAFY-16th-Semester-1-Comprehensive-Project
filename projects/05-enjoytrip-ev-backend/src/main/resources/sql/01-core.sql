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

CREATE TABLE IF NOT EXISTS signgu (
    sido_code   INT         NOT NULL,
    signgu_code INT         NOT NULL,
    name        VARCHAR(30) NOT NULL,
    PRIMARY KEY (sido_code, signgu_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS attractions (
    content_id      INT            NOT NULL,
    content_type_id INT            NOT NULL,
    title           VARCHAR(200)   NOT NULL,
    addr1           VARCHAR(200),
    addr2           VARCHAR(100),
    tel             VARCHAR(100),
    first_image     VARCHAR(300),
    first_image2    VARCHAR(300),
    latitude        DECIMAL(13, 10) NOT NULL,
    longitude       DECIMAL(13, 10) NOT NULL,
    sido_code       INT,
    signgu_code     INT,
    lcls1           VARCHAR(10),
    lcls2           VARCHAR(10),
    lcls3           VARCHAR(10),
    PRIMARY KEY (content_id),
    KEY idx_attractions_type (content_type_id),
    KEY idx_attractions_region (sido_code, signgu_code),
    CONSTRAINT fk_attractions_type FOREIGN KEY (content_type_id) REFERENCES content_types (content_type_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
