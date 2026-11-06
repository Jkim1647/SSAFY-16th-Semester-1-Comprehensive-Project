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
