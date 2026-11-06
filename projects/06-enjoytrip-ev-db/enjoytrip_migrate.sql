-- enjoytrip 구조 변경 + ssafy_trip(싸피 원본) 데이터 이식
-- 전제: 백업 완료 (C:\SSAFY\db_backup\enjoytrip_*.sql), ssafy_trip 에 원본 적재 완료
-- 실행: mysql -u ssafy -p < enjoytrip_migrate.sql

USE enjoytrip;

-- ============================================================
-- 1. 구조 변경 (DDL)
-- ============================================================

-- 시도 (싸피 sidos)
CREATE TABLE IF NOT EXISTS sidos (
    `no`      INT         NOT NULL AUTO_INCREMENT,
    sido_code INT         NOT NULL,
    sido_name VARCHAR(20),
    PRIMARY KEY (`no`),
    UNIQUE KEY uk_sidos_code (sido_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 구군 (싸피 guguns). 기존 signgu(27행, CSV)는 백엔드가 아직 쓰므로 그대로 둔다.
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

-- attractions: content_id 를 UNIQUE 로 내리고(plan_stops FK 유지), 대리키 no 를 PK 로 추가
ALTER TABLE attractions ADD UNIQUE KEY uk_attractions_content (content_id);
ALTER TABLE attractions
    DROP PRIMARY KEY,
    ADD COLUMN `no` INT NOT NULL AUTO_INCREMENT FIRST,
    ADD PRIMARY KEY (`no`);
ALTER TABLE attractions
    MODIFY latitude  DECIMAL(20,17) NOT NULL,
    MODIFY longitude DECIMAL(20,17) NOT NULL,
    ADD COLUMN overview VARCHAR(10000) NULL,
    ADD COLUMN homepage VARCHAR(1000)  NULL,
    ADD COLUMN map_level INT           NULL;
-- 조회 인덱스: 새 인덱스를 먼저 만들고(content_type_id FK 가 쓸 인덱스 유지) 예전 인덱스를 지운다
ALTER TABLE attractions
    ADD KEY idx_attractions_region_type (sido_code, signgu_code, content_type_id),
    ADD KEY idx_attractions_type_list (content_type_id, ((first_image IS NULL OR first_image = '')), title, content_id),
    ADD KEY idx_attractions_list (((first_image IS NULL OR first_image = '')), title, content_id);
ALTER TABLE attractions DROP KEY idx_attractions_region, DROP KEY idx_attractions_type;

-- ============================================================
-- 2. 데이터 이식 (한 트랜잭션)
-- ============================================================
START TRANSACTION;

-- 2-1. 콘텐츠 타입 (이미 있는 이름은 유지)
INSERT IGNORE INTO content_types (content_type_id, name)
    SELECT content_type_id, content_type_name FROM ssafy_trip.contenttypes;

-- 2-2. 시도 / 구군
INSERT INTO sidos (sido_code, sido_name)
    SELECT sido_code, sido_name FROM ssafy_trip.sidos;
INSERT INTO guguns (sido_code, gugun_code, gugun_name)
    SELECT sido_code, gugun_code, gugun_name FROM ssafy_trip.guguns;

-- 2-3. 관광지 (싸피 50,915건). 기존 CSV 행과 content_id 가 겹치면 싸피 값으로 갱신하되 lcls1~3 은 보존.
INSERT INTO attractions
    (content_id, content_type_id, title, addr1, addr2, tel, first_image, first_image2,
     latitude, longitude, sido_code, signgu_code, overview, homepage, map_level)
    SELECT content_id, content_type_id, title, addr1, addr2, tel, first_image1, first_image2,
           latitude, longitude, area_code, si_gun_gu_code, overview, homepage, map_level
    FROM ssafy_trip.attractions
ON DUPLICATE KEY UPDATE
    content_type_id = VALUES(content_type_id),
    title           = VALUES(title),
    addr1           = VALUES(addr1),
    addr2           = VALUES(addr2),
    tel             = VALUES(tel),
    first_image     = VALUES(first_image),
    first_image2    = VALUES(first_image2),
    latitude        = VALUES(latitude),
    longitude       = VALUES(longitude),
    sido_code       = VALUES(sido_code),
    signgu_code     = VALUES(signgu_code),
    overview        = VALUES(overview),
    homepage        = VALUES(homepage),
    map_level       = VALUES(map_level);

COMMIT;
