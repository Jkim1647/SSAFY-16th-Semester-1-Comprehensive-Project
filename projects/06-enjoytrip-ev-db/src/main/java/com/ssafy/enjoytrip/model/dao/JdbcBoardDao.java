package com.ssafy.enjoytrip.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.model.dto.Board;
import com.ssafy.enjoytrip.model.dto.SearchCondition;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.DataAccessException;

public class JdbcBoardDao implements BoardDao {

    private static final JdbcBoardDao INSTANCE = new JdbcBoardDao();

    /** 화면의 검색 key → 실제 컬럼. 이 표에 있는 문자열만 SQL 에 들어간다. */
    private static final Map<String, String> SEARCH_COLUMNS = Map.of("title", "b.title", "content", "b.content",
            "writer", "m.name");

    private static final String SELECT = "SELECT b.board_id, b.board_type, b.member_id, m.name AS writer_name, b.title,"
            + " b.content, b.view_count, b.created_at, b.updated_at FROM boards b JOIN members m ON m.member_id = b.member_id";

    /** 목록에는 본문이 필요 없어 content 를 빼고 가져온다. */
    private static final String SELECT_LIST = "SELECT b.board_id, b.board_type, b.member_id, m.name AS writer_name, b.title,"
            + " NULL AS content, b.view_count, b.created_at, b.updated_at FROM boards b JOIN members m ON m.member_id = b.member_id";

    private JdbcBoardDao() {
    }

    public static JdbcBoardDao getInstance() {
        return INSTANCE;
    }

    @Override
    public long insert(Board board) {
        String sql = "INSERT INTO boards (board_type, member_id, title, content) VALUES (?, ?, ?, ?)";
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, board.getBoardType());
            ps.setLong(2, board.getMemberId());
            ps.setString(3, board.getTitle());
            ps.setString(4, board.getContent());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("게시글 등록 실패", e);
        }
    }

    @Override
    public Board findById(long boardId) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement(SELECT + " WHERE b.board_id = ?")) {
            ps.setLong(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("게시글 조회 실패", e);
        }
    }

    private String where(SearchCondition c) {
        String where = " WHERE b.board_type = ?";
        if (c.hasKeyword()) {
            where += " AND " + SEARCH_COLUMNS.get(c.normalizedKey()) + " LIKE ?";
        }
        return where;
    }

    @Override
    public int count(String boardType, SearchCondition c) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT COUNT(*) FROM boards b JOIN members m ON m.member_id = b.member_id" + where(c))) {
            ps.setString(1, boardType);
            if (c.hasKeyword()) {
                ps.setString(2, c.likeWord());
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("게시글 수 조회 실패", e);
        }
    }

    @Override
    public List<Board> search(String boardType, SearchCondition c) {
        String sql = SELECT_LIST + where(c) + " ORDER BY b.board_id DESC LIMIT ? OFFSET ?";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, boardType);
            if (c.hasKeyword()) {
                ps.setString(i++, c.likeWord());
            }
            ps.setInt(i++, c.getItemsPerPage());
            ps.setInt(i, c.getOffset());
            return mapAll(ps);
        } catch (SQLException e) {
            throw new DataAccessException("게시글 목록 조회 실패", e);
        }
    }

    @Override
    public List<Board> findLatest(String boardType, int limit) {
        String sql = SELECT_LIST + " WHERE b.board_type = ? ORDER BY b.board_id DESC LIMIT ?";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, boardType);
            ps.setInt(2, limit);
            return mapAll(ps);
        } catch (SQLException e) {
            throw new DataAccessException("최신 게시글 조회 실패", e);
        }
    }

    @Override
    public int update(long boardId, String title, String content) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE boards SET title = ?, content = ? WHERE board_id = ?")) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setLong(3, boardId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("게시글 수정 실패", e);
        }
    }

    @Override
    public int increaseViewCount(long boardId) {
        // updated_at 은 "글 수정 시각"이므로 조회수 증가로 바뀌지 않게 그대로 다시 넣는다
        String sql = "UPDATE boards SET view_count = view_count + 1, updated_at = updated_at WHERE board_id = ?";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, boardId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("조회수 증가 실패", e);
        }
    }

    @Override
    public int delete(long boardId) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM boards WHERE board_id = ?")) {
            ps.setLong(1, boardId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("게시글 삭제 실패", e);
        }
    }

    private List<Board> mapAll(PreparedStatement ps) throws SQLException {
        List<Board> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private Board map(ResultSet rs) throws SQLException {
        Board b = new Board();
        b.setBoardId(rs.getLong("board_id"));
        b.setBoardType(rs.getString("board_type"));
        b.setMemberId(rs.getLong("member_id"));
        b.setWriterName(rs.getString("writer_name"));
        b.setTitle(rs.getString("title"));
        b.setContent(rs.getString("content"));
        b.setViewCount(rs.getInt("view_count"));
        b.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        b.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return b;
    }
}
