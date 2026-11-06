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
