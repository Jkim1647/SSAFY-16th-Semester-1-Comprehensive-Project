package com.ssafy.enjoytrip.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.enjoytrip.model.dto.Member;
import com.ssafy.enjoytrip.model.dto.SearchCondition;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.DataAccessException;

public class JdbcMemberDao implements MemberDao {

    private static final JdbcMemberDao INSTANCE = new JdbcMemberDao();
    private static final String COLUMNS = "member_id, email, name, password_hash, role, created_at";

    private JdbcMemberDao() {
    }

    public static JdbcMemberDao getInstance() {
        return INSTANCE;
    }

    @Override
    public long insert(Member member) {
        String sql = "INSERT INTO members (email, name, password_hash, role) VALUES (?, ?, ?, ?)";
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, member.getEmail());
            ps.setString(2, member.getName());
            ps.setString(3, member.getPasswordHash());
            ps.setString(4, member.getRole());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("회원 등록 실패", e);
        }
    }

    @Override
    public Member findByEmail(String email) {
        return findOne("SELECT " + COLUMNS + " FROM members WHERE email = ?", email);
    }

    @Override
    public Member findById(long memberId) {
        return findOne("SELECT " + COLUMNS + " FROM members WHERE member_id = ?", memberId);
    }

    private Member findOne(String sql, Object param) {
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("회원 조회 실패", e);
        }
    }

    /** key 는 SearchCondition 허용 목록(name, email)을 거친 값만 SQL 에 들어간다. */
    private String where(SearchCondition c) {
        return c.hasKeyword() ? " WHERE " + c.normalizedKey() + " LIKE ?" : "";
    }

    @Override
    public int count(SearchCondition c) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM members" + where(c))) {
            if (c.hasKeyword()) {
                ps.setString(1, c.likeWord());
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("회원 수 조회 실패", e);
        }
    }

    @Override
    public List<Member> search(SearchCondition c) {
        String sql = "SELECT " + COLUMNS + " FROM members" + where(c) + " ORDER BY member_id DESC LIMIT ? OFFSET ?";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            if (c.hasKeyword()) {
                ps.setString(i++, c.likeWord());
            }
            ps.setInt(i++, c.getItemsPerPage());
            ps.setInt(i, c.getOffset());
            List<Member> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("회원 목록 조회 실패", e);
        }
    }

    @Override
    public int updateName(long memberId, String name) {
        return update("UPDATE members SET name = ? WHERE member_id = ?", name, memberId);
    }

    @Override
    public int updatePassword(long memberId, String passwordHash) {
        return update("UPDATE members SET password_hash = ? WHERE member_id = ?", passwordHash, memberId);
    }

    @Override
    public int delete(long memberId) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM members WHERE member_id = ?")) {
            ps.setLong(1, memberId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("회원 삭제 실패", e);
        }
    }

    @Override
    public void saveResetToken(long memberId, String tokenHash, LocalDateTime expiresAt) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement del = con.prepareStatement("DELETE FROM password_resets WHERE member_id = ?");
                PreparedStatement ins = con.prepareStatement(
                        "INSERT INTO password_resets (token_hash, member_id, expires_at) VALUES (?, ?, ?)")) {
            del.setLong(1, memberId);
            del.executeUpdate();
            ins.setString(1, tokenHash);
            ins.setLong(2, memberId);
            ins.setTimestamp(3, Timestamp.valueOf(expiresAt));
            ins.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("재설정 토큰 저장 실패", e);
        }
    }

    @Override
    public boolean consumeResetToken(String tokenHash, String newPasswordHash) {
        Connection con = null;
        try {
            con = DBUtil.getConnection();
            con.setAutoCommit(false);
            long memberId;
            try (PreparedStatement ps = con.prepareStatement("SELECT member_id FROM password_resets"
                    + " WHERE token_hash = ? AND used_at IS NULL AND expires_at > NOW() FOR UPDATE")) {
                ps.setString(1, tokenHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        return false;
                    }
                    memberId = rs.getLong(1);
                }
            }
            try (PreparedStatement used = con.prepareStatement("UPDATE password_resets SET used_at = NOW() WHERE token_hash = ?");
                    PreparedStatement pw = con.prepareStatement("UPDATE members SET password_hash = ? WHERE member_id = ?")) {
                used.setString(1, tokenHash);
                used.executeUpdate();
                pw.setString(1, newPasswordHash);
                pw.setLong(2, memberId);
                pw.executeUpdate();
            }
            con.commit();
            return true;
        } catch (SQLException e) {
            DBUtil.rollback(con);
            throw new DataAccessException("비밀번호 재설정 실패", e);
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (SQLException e) {
                    System.err.println("연결 반납 실패: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public boolean isResetTokenValid(String tokenHash) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT 1 FROM password_resets"
                        + " WHERE token_hash = ? AND used_at IS NULL AND expires_at > NOW()")) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("재설정 토큰 확인 실패", e);
        }
    }

    private int update(String sql, String value, long memberId) {
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setLong(2, memberId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("회원 수정 실패", e);
        }
    }

    private Member map(ResultSet rs) throws SQLException {
        Member m = new Member(rs.getString("email"), rs.getString("name"));
        m.setMemberId(rs.getLong("member_id"));
        m.setPasswordHash(rs.getString("password_hash"));
        m.setRole(rs.getString("role"));
        m.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return m;
    }
}
