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
