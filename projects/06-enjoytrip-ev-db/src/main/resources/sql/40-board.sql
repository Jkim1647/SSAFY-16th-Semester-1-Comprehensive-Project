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
