package com.ssafy.enjoytrip.model.dao;

import java.sql.*;
import java.util.*;
import com.ssafy.enjoytrip.model.dto.*;
import com.ssafy.enjoytrip.util.*;

/**
 * JDBC operations for the unified community. Mutations share a transaction
 * connection.
 */
public class JdbcBoardCommunityDao implements BoardCommunityDao {
    @FunctionalInterface
    public interface Connections {
        Connection open() throws SQLException;
    }
    private final Connections connections;
    public JdbcBoardCommunityDao(Connections connections) {
        this.connections = connections;
    }
    public JdbcBoardCommunityDao() {
        this(DBUtil::getConnection);
    }
    public <T> T read(Work<T> work) {
        try (Connection c = connections.open()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new DataAccessException("게시판 조회 실패", e);
        }
    }
    public <T> T transaction(Work<T> work) {
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                DBUtil.rollback(c);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("게시판 저장 실패", e);
        }
    }
    public int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = prepare(c, sql, args)) {
            return p.executeUpdate();
        }
    }
    public long insert(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(p, args);
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                if (!r.next())
                    throw new SQLException("생성된 ID 없음");
                return r.getLong(1);
            }
        }
    }
    private static PreparedStatement prepare(Connection c, String sql, Object... args) throws SQLException {
        PreparedStatement p = c.prepareStatement(sql);
        bind(p, args);
        return p;
    }
    private static void bind(PreparedStatement p, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++)
            p.setObject(i + 1, args[i]);
    }
    private static final String FROM = " FROM boards b JOIN members m ON m.member_id=b.member_id LEFT JOIN board_settings s ON s.board_id=b.board_id ";
    private static final String VISIBLE = "COALESCE(s.hidden,0)=0";
    private static final String ACTIVE_PIN = "(b.board_type='NOTICE' AND COALESCE(s.pinned,0)=1 AND "
            + "(s.pin_start IS NULL OR s.pin_start<=(UTC_TIMESTAMP() + INTERVAL 9 HOUR)) AND (s.pin_end IS NULL OR s.pin_end>(UTC_TIMESTAMP() + INTERVAL 9 HOUR)))";
    private static final String SELECT = "SELECT b.*,m.name writer_name,COALESCE(s.category,'GENERAL') category,"
            + "COALESCE(s.hidden,0) hidden,COALESCE(s.pinned,0) pinned,COALESCE(s.pin_order,0) pin_order,s.pin_start,s.pin_end,"
            + "(SELECT COUNT(*) FROM board_likes l WHERE l.board_id=b.board_id) like_count,"
            + "(SELECT COUNT(*) FROM board_comments c WHERE c.board_id=b.board_id AND c.hidden=0) comment_count,"
            + "EXISTS(SELECT 1 FROM board_likes l WHERE l.board_id=b.board_id AND l.member_id=?) liked,"
            + "EXISTS(SELECT 1 FROM board_bookmarks k WHERE k.board_id=b.board_id AND k.member_id=?) bookmarked";
    private static final String SELECT_LIST = SELECT.replace("b.*",
            "b.board_id,b.board_type,b.member_id,b.title,NULL AS content,b.view_count,b.created_at,b.updated_at");
    public BoardEntry get(Connection c, long id, long viewer, boolean lock) throws SQLException {
        if (lock) {
            try (PreparedStatement p = prepare(c, "SELECT board_id FROM boards WHERE board_id=? FOR UPDATE", id);
                    ResultSet r = p.executeQuery()) {
                if (!r.next())
                    return null;
            }
        }
        try (PreparedStatement p = prepare(c, SELECT + FROM + "WHERE b.board_id=?", viewer, viewer, id);
                ResultSet r = p.executeQuery()) {
            return r.next() ? entry(r) : null;
        }
    }
    private String where(BoardQuery q, long viewer, boolean admin, List<Object> args) {
        String w = " WHERE " + (admin ? "1=1" : VISIBLE);
        if (!q.getType().isEmpty()) {
            w += " AND b.board_type=?";
            args.add(q.getType().toUpperCase(Locale.ROOT));
        }
        if (!q.getCategory().isEmpty()) {
            w += " AND COALESCE(s.category,'GENERAL')=?";
            args.add(q.getCategory());
        }
        if (q.hasKeyword()) {
            w += " AND " + Map.of("title", "b.title", "content", "b.content", "writer", "m.name").get(q.normalizedKey())
                    + " LIKE ?";
            args.add(q.likeWord());
        }
        switch (q.getActivity()) {
            case "posts" -> {
                w += " AND b.member_id=?";
                args.add(viewer);
            }
            case "likes", "bookmarks" -> {
                String table = q.getActivity().equals("likes") ? "board_likes" : "board_bookmarks";
                w += " AND EXISTS(SELECT 1 FROM " + table + " a WHERE a.board_id=b.board_id AND a.member_id=?)";
                args.add(viewer);
            }
            default -> {
            }
        }
        if (q.isPinSection())
            w += " AND NOT (" + ACTIVE_PIN + " AND " + VISIBLE + ")";
        return w;
    }
    public Page<BoardEntry> search(Connection c, BoardQuery q, long viewer, boolean admin) throws SQLException {
        List<Object> args = new ArrayList<>();
        String w = where(q, viewer, admin, args);
        int count = count(c, "SELECT COUNT(*)" + FROM + w, args.toArray());
        String order = switch (q.getSort()) {
            case "views" -> "b.view_count DESC,b.board_id DESC";
            case "likes" -> "like_count DESC,b.board_id DESC";
            default -> "b.board_id DESC";
        };
        if (q.getActivity().equals("bookmarks") || q.getActivity().equals("likes")) {
            String table = q.getActivity().equals("likes") ? "board_likes" : "board_bookmarks";
            if (q.getSort().equals("latest")) {
                order = "(SELECT a.created_at FROM " + table
                        + " a WHERE a.board_id=b.board_id AND a.member_id=?) DESC,b.board_id DESC";
            }
        }
        List<Object> all = new ArrayList<>(List.of(viewer, viewer));
        all.addAll(args);
        if (q.getSort().equals("latest") && Set.of("likes", "bookmarks").contains(q.getActivity()))
            all.add(viewer);
        all.add(q.getItemsPerPage());
        all.add(q.getOffset());
        try (PreparedStatement p = prepare(c, SELECT_LIST + FROM + w + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                all.toArray()); ResultSet r = p.executeQuery()) {
            List<BoardEntry> list = new ArrayList<>();
            while (r.next())
                list.add(entry(r));
            return new Page<>(q, count, list);
        }
    }
    public List<BoardEntry> pinned(Connection c, long viewer) throws SQLException {
        try (PreparedStatement p = prepare(c,
                SELECT + FROM + " WHERE " + VISIBLE + " AND " + ACTIVE_PIN + " ORDER BY s.pin_order,b.board_id DESC",
                viewer, viewer);
                ResultSet r = p.executeQuery()) {
            List<BoardEntry> list = new ArrayList<>();
            while (r.next())
                list.add(entry(r));
            return list;
        }
    }
    public void ensureSettings(Connection c, long id) throws SQLException {
        update(c, "INSERT INTO board_settings(board_id) VALUES(?) ON DUPLICATE KEY UPDATE board_id=board_id", id);
    }
    public void relation(Connection c, String table, long id, long viewer, boolean active) throws SQLException {
        if (!Set.of("board_likes", "board_bookmarks").contains(table))
            throw new IllegalArgumentException("Unknown relation");
        if (active)
            update(c, "INSERT INTO " + table
                    + "(board_id,member_id) VALUES(?,?) ON DUPLICATE KEY UPDATE board_id=board_id", id, viewer);
        else
            update(c, "DELETE FROM " + table + " WHERE board_id=? AND member_id=?", id, viewer);
    }
    private static final String COMMENT_FROM = " FROM board_comments c "
            + "JOIN members m ON m.member_id=c.member_id JOIN boards b ON b.board_id=c.board_id LEFT JOIN board_settings s ON s.board_id=b.board_id";
    private static String commentSelect(String position) {
        return "SELECT c.*,m.name writer_name,b.title board_title," + position + " visible_position" + COMMENT_FROM;
    }
    public BoardComment comment(Connection c, long boardId, long id, boolean lock) throws SQLException {
        try (PreparedStatement p = prepare(c,
                commentSelect("0") + " WHERE c.board_id=? AND c.comment_id=?" + (lock ? " FOR UPDATE" : ""), boardId,
                id);
                ResultSet r = p.executeQuery()) {
            return r.next() ? comment(r) : null;
        }
    }
    public Page<BoardComment> comments(Connection c, long boardId, long viewer, boolean admin, int page, boolean mine)
            throws SQLException {
        String w = mine ? " WHERE c.member_id=?" : " WHERE c.board_id=?";
        if (!admin || mine)
            w += " AND c.hidden=0 AND " + VISIBLE;
        long value = mine ? viewer : boardId;
        SearchCondition condition = new SearchCondition(Set.of(), "", "", Math.min(100000, Math.max(1, page)),
                mine ? 10 : 20);
        int count = count(c, "SELECT COUNT(*)" + COMMENT_FROM + w, value);
        String position = mine
                ? "(SELECT COUNT(*) FROM board_comments prev WHERE prev.board_id=c.board_id AND prev.comment_id<=c.comment_id"
                        + (admin ? "" : " AND prev.hidden=0") + ")"
                : "0";
        try (PreparedStatement p = prepare(c, commentSelect(position)
                + w + " ORDER BY c.comment_id " + (mine ? "DESC" : "ASC") + " LIMIT ? OFFSET ?", value,
                condition.getItemsPerPage(), condition.getOffset()); ResultSet r = p.executeQuery()) {
            List<BoardComment> list = new ArrayList<>();
            while (r.next())
                list.add(comment(r));
            return new Page<>(condition, count, list);
        }
    }
    public List<BoardComment> allComments(Connection c, long boardId, boolean admin) throws SQLException {
        String where = " WHERE c.board_id=?" + (admin ? "" : " AND c.hidden=0 AND " + VISIBLE);
        try (PreparedStatement p = prepare(c, commentSelect("0") + where + " ORDER BY c.comment_id ASC", boardId);
             ResultSet r = p.executeQuery()) {
            List<BoardComment> result = new ArrayList<>();
            while (r.next()) result.add(comment(r));
            return result;
        }
    }
    public int commentPage(Connection c, long boardId, long cid, boolean admin) throws SQLException {
        int position = count(c, "SELECT COUNT(*) FROM board_comments WHERE board_id=? AND comment_id<=?"
                + (admin ? "" : " AND hidden=0"), boardId, cid);
        return Math.max(1, (position + 19) / 20);
    }
    public Page<Map<String, Object>> reports(Connection c, int page) throws SQLException {
        SearchCondition cond = new SearchCondition(Set.of(), "", "", Math.min(100000, Math.max(1, page)), 10);
        int total = count(c, "SELECT COUNT(*) FROM board_reports WHERE comment_id IS NOT NULL");
        try (PreparedStatement p = prepare(c, "SELECT r.*,b.title,m.name reporter_name,cm.content reported_content, "
                + "(SELECT COUNT(*) FROM board_comments prev WHERE prev.board_id=r.board_id AND prev.comment_id<=r.comment_id) comment_position FROM board_reports r "
                + "JOIN boards b ON b.board_id=r.board_id JOIN members m ON m.member_id=r.member_id LEFT JOIN board_comments cm ON cm.comment_id=r.comment_id "
                + "WHERE r.comment_id IS NOT NULL ORDER BY (r.status='OPEN') DESC,r.report_id DESC LIMIT ? OFFSET ?", cond.getItemsPerPage(),
                cond.getOffset()); ResultSet r = p.executeQuery()) {
            List<Map<String, Object>> list = new ArrayList<>();
            while (r.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("reportId", r.getLong("report_id"));
                row.put("boardId", r.getLong("board_id"));
                row.put("commentId", r.getLong("comment_id"));
                for (String col : List.of("title", "reason", "status", "resolution"))
                    row.put(col, r.getString(col));
                row.put("commentPage", Math.max(1, (r.getInt("comment_position") + 19) / 20));
                row.put("reporter", r.getString("reporter_name"));
                row.put("reportedContent", r.getString("reported_content"));
                row.put("createdAt", r.getTimestamp("created_at"));
                list.add(row);
            }
            return new Page<>(cond, total, list);
        }
    }
    public List<Map<String, Object>> history(Connection c, long id) throws SQLException {
        try (PreparedStatement p = prepare(c,
                "SELECT h.*,m.name admin_name FROM board_moderation h LEFT JOIN members m ON m.member_id=h.admin_id WHERE h.board_id=? ORDER BY h.moderation_id DESC LIMIT 50",
                id); ResultSet r = p.executeQuery()) {
            List<Map<String, Object>> list = new ArrayList<>();
            while (r.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("action", r.getString("action"));
                row.put("reason", r.getString("reason"));
                row.put("commentId", r.getLong("comment_id"));
                row.put("adminName", r.getString("admin_name"));
                row.put("createdAt", r.getTimestamp("created_at"));
                list.add(row);
            }
            return list;
        }
    }
    private int count(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = prepare(c, sql, args); ResultSet r = p.executeQuery()) {
            r.next();
            return r.getInt(1);
        }
    }
    private static java.time.LocalDateTime time(ResultSet r, String name) throws SQLException {
        Timestamp t = r.getTimestamp(name);
        return t == null ? null : t.toLocalDateTime();
    }
    private BoardEntry entry(ResultSet r) throws SQLException {
        BoardEntry b = new BoardEntry();
        b.setBoardId(r.getLong("board_id"));
        b.setBoardType(r.getString("board_type"));
        b.setMemberId(r.getLong("member_id"));
        b.setWriterName(r.getString("writer_name"));
        b.setTitle(r.getString("title"));
        b.setContent(r.getString("content"));
        b.setViewCount(r.getInt("view_count"));
        b.setCreatedAt(time(r, "created_at"));
        b.setUpdatedAt(time(r, "updated_at"));
        b.setCategory(r.getString("category"));
        b.setHidden(r.getBoolean("hidden"));
        b.setPinned(r.getBoolean("pinned"));
        b.setPinOrder(r.getInt("pin_order"));
        b.setPinStart(time(r, "pin_start"));
        b.setPinEnd(time(r, "pin_end"));
        b.setLikeCount(r.getInt("like_count"));
        b.setCommentCount(r.getInt("comment_count"));
        b.setLiked(r.getBoolean("liked"));
        b.setBookmarked(r.getBoolean("bookmarked"));
        return b;
    }
    private BoardComment comment(ResultSet r) throws SQLException {
        BoardComment v = new BoardComment();
        v.setCommentId(r.getLong("comment_id"));
        v.setBoardId(r.getLong("board_id"));
        v.setMemberId(r.getLong("member_id"));
        v.setWriterName(r.getString("writer_name"));
        v.setBoardTitle(r.getString("board_title"));
        v.setContent(r.getString("content"));
        v.setHidden(r.getBoolean("hidden"));
        v.setPage(Math.max(1, (r.getInt("visible_position") + 19) / 20));
        v.setCreatedAt(time(r, "created_at"));
        v.setUpdatedAt(time(r, "updated_at"));
        return v;
    }
}
