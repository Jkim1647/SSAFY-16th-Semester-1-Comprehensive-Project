package com.ssafy.enjoytrip.model.service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import com.ssafy.enjoytrip.model.dao.BoardCommunityDao;
import com.ssafy.enjoytrip.model.dao.JdbcBoardCommunityDao;
import com.ssafy.enjoytrip.model.dto.*;

/**
 * Policies and transaction boundaries; every mutation locks its parent post
 * first.
 */
public class BoardCommunityService {
    private static final BoardCommunityService INSTANCE = new BoardCommunityService(new JdbcBoardCommunityDao());
    public static BoardCommunityService getInstance() {
        return INSTANCE;
    }
    private final BoardCommunityDao dao;
    public BoardCommunityService(BoardCommunityDao dao) {
        this.dao = dao;
    }
    public static class Problem extends RuntimeException {
        private final int status;
        public Problem(int status, String message) {
            super(message);
            this.status = status;
        }
        public int getStatus() {
            return status;
        }
    }
    private static Problem bad(String message) {
        return new Problem(400, message);
    }
    private static long viewer(Member user) {
        return user == null ? 0 : user.getMemberId();
    }
    private static boolean admin(Member user) {
        return user != null && user.isAdmin();
    }
    private static void login(Member user) {
        if (user == null)
            throw new Problem(401, "로그인이 필요합니다.");
    }
    private static void administrator(Member user) {
        login(user);
        if (!admin(user))
            throw new Problem(403, "관리자만 사용할 수 있습니다.");
    }
    private static String text(String s, int max, String label) {
        String v = s == null ? "" : s.replace("\r\n", "\n").strip();
        if (v.isEmpty() || v.length() > max)
            throw bad(label + "은 1~" + max + "자로 입력하세요.");
        return v;
    }
    private static String category(String value) {
        if (!BoardQuery.CATEGORIES.contains(value == null ? "" : value))
            throw bad("지원하지 않는 분류입니다.");
        return value;
    }
    private BoardEntry available(Connection c, long id, Member user, boolean lock) throws SQLException {
        BoardEntry b = dao.get(c, id, viewer(user), lock);
        if (b == null || (b.isHidden() && !admin(user)))
            throw new Problem(404, "게시글을 찾을 수 없습니다.");
        return b;
    }
    private void manage(BoardEntry b, Member user) {
        login(user);
        if (!admin(user) && (b.isNotice() || b.getMemberId() != user.getMemberId()))
            throw new Problem(403, "수정·삭제 권한이 없습니다.");
    }
    private BoardComment comment(Connection c, long boardId, long cid, Member user, boolean owner) throws SQLException {
        BoardComment v = dao.comment(c, boardId, cid, true);
        if (v == null || (v.isHidden() && !admin(user)))
            throw new Problem(404, "댓글을 찾을 수 없습니다.");
        if (owner && !admin(user) && v.getMemberId() != user.getMemberId())
            throw new Problem(403, "댓글 수정·삭제 권한이 없습니다.");
        return v;
    }
    public Page<BoardEntry> search(Member user, BoardQuery q) {
        if (!q.getActivity().isEmpty())
            login(user);
        return dao.read(c -> dao.search(c, q, viewer(user), admin(user)));
    }
    public List<BoardEntry> pinned(Member user, BoardQuery q) {
        return q.isPinSection() && q.getCurrentPage() == 1 ? dao.read(c -> dao.pinned(c, viewer(user))) : List.of();
    }
    public BoardEntry get(long id, Member user) {
        return dao.read(c -> available(c, id, user, false));
    }
    public long write(Member user, String type, String title, String content, String category) {
        login(user);
        if (!Set.of("FREE", "NOTICE").contains(type == null ? "" : type))
            throw bad("지원하지 않는 글 유형입니다.");
        if (type.equals("NOTICE"))
            administrator(user);
        String t = text(title, 200, "제목"), body = text(content, 10000, "내용"), cat = category(category);
        return dao.transaction(c -> {
            long id = dao.insert(c, "INSERT INTO boards(board_type,member_id,title,content) VALUES(?,?,?,?)", type,
                    user.getMemberId(), t, body);
            dao.update(c, "INSERT INTO board_settings(board_id,category) VALUES(?,?)", id, cat);
            return id;
        });
    }
    public void modify(Member user, long id, String title, String content, String category) {
        login(user);
        String t = text(title, 200, "제목"), body = text(content, 10000, "내용"), cat = category(category);
        dao.transaction(c -> {
            BoardEntry b = available(c, id, user, true);
            manage(b, user);
            dao.update(c, "UPDATE boards SET title=?,content=? WHERE board_id=?", t, body, id);
            dao.ensureSettings(c, id);
            dao.update(c, "UPDATE board_settings SET category=? WHERE board_id=?", cat, id);
            return null;
        });
    }
    public void delete(Member user, long id) {
        login(user);
        dao.transaction(c -> {
            BoardEntry b = available(c, id, user, true);
            manage(b, user);
            dao.update(c, "DELETE FROM boards WHERE board_id=?", id);
            return null;
        });
    }
    public Map<String, Object> react(Member user, long id, String kind, boolean active) {
        login(user);
        String table = switch (kind == null ? "" : kind) {
            case "like" -> "board_likes";
            case "bookmark" -> "board_bookmarks";
            default -> throw bad("지원하지 않는 반응입니다.");
        };
        return dao.transaction(c -> {
            available(c, id, user, true);
            dao.relation(c, table, id, user.getMemberId(), active);
            BoardEntry b = dao.get(c, id, user.getMemberId(), false);
            return Map.of("liked", b.isLiked(), "bookmarked", b.isBookmarked(), "likeCount", b.getLikeCount());
        });
    }
    public Page<BoardComment> comments(long id, Member user, int page) {
        return dao.read(c -> {
            available(c, id, user, false);
            return dao.comments(c, id, viewer(user), admin(user), page, false);
        });
    }
    public List<BoardComment> allComments(long id, Member user) {
        return dao.read(c -> { available(c, id, user, false); return dao.allComments(c, id, admin(user)); });
    }
    public Page<BoardComment> myComments(Member user, int page) {
        login(user);
        return dao.read(c -> dao.comments(c, 0, user.getMemberId(), admin(user), page, true));
    }
    public int commentPage(Member user, long id, long cid) {
        return dao.read(c -> {
            available(c, id, user, false);
            return dao.commentPage(c, id, cid, admin(user));
        });
    }
    public long addComment(Member user, long id, String content) {
        login(user);
        String body = text(content, 2000, "댓글");
        return dao.transaction(c -> {
            available(c, id, user, true);
            return dao.insert(c, "INSERT INTO board_comments(board_id,member_id,content) VALUES(?,?,?)", id,
                    user.getMemberId(), body);
        });
    }
    public void editComment(Member user, long id, long cid, String content) {
        login(user);
        String body = text(content, 2000, "댓글");
        dao.transaction(c -> {
            available(c, id, user, true);
            comment(c, id, cid, user, true);
            dao.update(c, "UPDATE board_comments SET content=? WHERE comment_id=?", body, cid);
            return null;
        });
    }
    public void deleteComment(Member user, long id, long cid) {
        login(user);
        dao.transaction(c -> {
            available(c, id, user, true);
            comment(c, id, cid, user, true);
            dao.update(c, "DELETE FROM board_comments WHERE comment_id=?", cid);
            return null;
        });
    }
    public void report(Member user, long id, long cid, String reason) {
        login(user);
        if (cid <= 0) throw bad("게시글 신고 기능은 제공하지 않습니다.");
        String why = text(reason, 500, "신고 사유");
        dao.transaction(c -> {
            available(c, id, user, true);
            comment(c, id, cid, user, false);
            dao.update(c,
                    "INSERT INTO board_reports(board_id,comment_id,member_id,target_key,reason) VALUES(?,?,?,?,?) "
                            + "ON DUPLICATE KEY UPDATE report_id=report_id",
                    id, cid, user.getMemberId(), "C:" + cid, why);
            return null;
        });
    }
    public Page<Map<String, Object>> reports(Member user, int page) {
        administrator(user);
        return dao.read(c -> dao.reports(c, page));
    }
    public void resolveReport(Member user, long rid, String reason) {
        administrator(user);
        String why = text(reason, 500, "처리 사유");
        dao.transaction(c -> {
            int n = dao.update(c,
                    "UPDATE board_reports SET status='RESOLVED',resolution=?,resolved_by=?,resolved_at=CURRENT_TIMESTAMP WHERE report_id=? AND status='OPEN' AND comment_id IS NOT NULL",
                    why, user.getMemberId(), rid);
            if (n == 0)
                throw new Problem(409, "이미 처리되었거나 존재하지 않는 신고입니다.");
            return null;
        });
    }
    public void moderate(Member user, long id, long cid, boolean hidden, String reason) {
        administrator(user);
        String why = text(reason, 500, "관리 사유");
        dao.transaction(c -> {
            available(c, id, user, true);
            if (cid > 0) {
                comment(c, id, cid, user, false);
                dao.update(c, "UPDATE board_comments SET hidden=?,updated_at=updated_at WHERE comment_id=?", hidden,
                        cid);
            } else {
                dao.ensureSettings(c, id);
                dao.update(c, "UPDATE board_settings SET hidden=? WHERE board_id=?", hidden, id);
            }
            audit(c, id, cid, user, hidden ? "HIDE" : "RESTORE", why);
            return null;
        });
    }
    public void pin(Member user, long id, boolean pinned, int order, LocalDateTime start, LocalDateTime end) {
        administrator(user);
        if (order < 0 || order > 999)
            throw bad("고정 순서는 0~999입니다.");
        if (start != null && end != null && !end.isAfter(start))
            throw bad("종료 시각은 시작 시각보다 뒤여야 합니다.");
        dao.transaction(c -> {
            BoardEntry b = available(c, id, user, true);
            if (!b.isNotice())
                throw bad("공지 글만 고정할 수 있습니다.");
            dao.ensureSettings(c, id);
            dao.update(c, "UPDATE board_settings SET pinned=?,pin_order=?,pin_start=?,pin_end=? WHERE board_id=?",
                    pinned, order, start, end, id);
            audit(c, id, 0, user, pinned ? "PIN" : "UNPIN", "순서 " + order + ", 시작 " + start + ", 종료 " + end);
            return null;
        });
    }
    private void audit(Connection c, long id, long cid, Member user, String action, String reason) throws SQLException {
        dao.update(c, "INSERT INTO board_moderation(board_id,comment_id,admin_id,action,reason) VALUES(?,?,?,?,?)", id,
                cid > 0 ? cid : null, user.getMemberId(), action, reason);
    }
    public List<Map<String, Object>> history(Member user, long id) {
        administrator(user);
        return dao.read(c -> dao.history(c, id));
    }
}
