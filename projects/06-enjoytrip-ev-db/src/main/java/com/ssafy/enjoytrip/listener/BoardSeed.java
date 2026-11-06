package com.ssafy.enjoytrip.listener;

import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ssafy.enjoytrip.model.dto.BoardQuery;
import com.ssafy.enjoytrip.util.Csv;

/** Install sample posts once, only if the board is empty on first setup. */
public final class BoardSeed {
    private BoardSeed() { }

    public static void seed(Connection con) throws SQLException {
        List<Map<String, String>> rows = Csv.readResource("data/board-samples.csv");
        boolean previous = con.getAutoCommit();
        if (!previous) throw new SQLException("게시판 샘플 초기화에는 독립 트랜잭션이 필요합니다.");
        con.setAutoCommit(false);
        try {
            int inserted;
            try (PreparedStatement marker = con.prepareStatement("INSERT IGNORE INTO board_seed_state(seed_key) VALUES (?)")) {
                marker.setString(1, "board-samples-v1");
                inserted = marker.executeUpdate();
            }
            if (inserted == 1 && empty(con)) {
                long memberId;
                try (PreparedStatement member = con.prepareStatement(
                        "INSERT INTO members(email,name,password_hash,role) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    member.setString(1, "board-sample-" + UUID.randomUUID() + "@example.invalid");
                    member.setString(2, "여행 샘플");
                    member.setString(3, "!"); // Invalid hash: this sample author cannot log in.
                    member.setString(4, "USER");
                    member.executeUpdate();
                    try (ResultSet keys = member.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("샘플 회원 ID 없음"); memberId = keys.getLong(1); }
                }
                try (PreparedStatement post = con.prepareStatement("INSERT INTO boards(board_type,member_id,title,content) VALUES('FREE',?,?,?)", Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement setting = con.prepareStatement("INSERT INTO board_settings(board_id,category) VALUES(?,?)")) {
                    for (Map<String, String> row : rows) {
                        String category = row.get("category"), title = row.get("title"), content = row.get("content");
                        if (!BoardQuery.CATEGORIES.contains(category) || title == null || title.isBlank() || title.length() > 200
                                || content == null || content.isBlank() || content.length() > 10000) throw new SQLException("잘못된 게시판 샘플 데이터");
                        post.setLong(1, memberId); post.setString(2, title); post.setString(3, content); post.executeUpdate();
                        try (ResultSet keys = post.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("샘플 게시글 ID 없음"); setting.setLong(1, keys.getLong(1)); }
                        setting.setString(2, category); setting.executeUpdate();
                    }
                }
            }
            con.commit();
        } catch (SQLException | RuntimeException e) {
            con.rollback(); throw e;
        } finally { con.setAutoCommit(previous); }
    }
    public static void seedDetails(Connection con) throws SQLException { BoardSampleDetails.seed(con); }

    private static boolean empty(Connection con) throws SQLException {
        try (Statement s = con.createStatement(); ResultSet r = s.executeQuery("SELECT 1 FROM boards LIMIT 1")) { return !r.next(); }
    }
}
