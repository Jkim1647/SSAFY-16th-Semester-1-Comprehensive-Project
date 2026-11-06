package com.ssafy.enjoytrip.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.enjoytrip.model.dto.Hotplace;
import com.ssafy.enjoytrip.model.dto.HotplaceCondition;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.DataAccessException;

public class JdbcHotplaceDao implements HotplaceDao {

    private static final JdbcHotplaceDao INSTANCE = new JdbcHotplaceDao();
    private static final String SELECT = "SELECT h.*, m.name AS writer_name FROM hotplaces h"
            + " JOIN members m ON m.member_id = h.member_id";

    private JdbcHotplaceDao() {
    }

    public static JdbcHotplaceDao getInstance() {
        return INSTANCE;
    }

    @Override
    public long insert(Hotplace h) {
        String sql = "INSERT INTO hotplaces (member_id, title, place_type, visited_on, description, latitude, longitude, photo_file)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, h.getMemberId());
            ps.setString(2, h.getTitle());
            ps.setString(3, h.getPlaceType());
            ps.setObject(4, h.getVisitedOn());
            ps.setString(5, h.getDescription());
            ps.setDouble(6, h.getLatitude());
            ps.setDouble(7, h.getLongitude());
            ps.setString(8, h.getPhotoFile());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("핫플레이스 등록 실패", e);
        }
    }

    @Override
    public int update(Hotplace h) {
        String sql = "UPDATE hotplaces SET title = ?, place_type = ?, visited_on = ?, description = ?,"
                + " latitude = ?, longitude = ?, photo_file = ? WHERE hotplace_id = ?";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, h.getTitle());
            ps.setString(2, h.getPlaceType());
            ps.setObject(3, h.getVisitedOn());
            ps.setString(4, h.getDescription());
            ps.setDouble(5, h.getLatitude());
            ps.setDouble(6, h.getLongitude());
            ps.setString(7, h.getPhotoFile());
            ps.setLong(8, h.getHotplaceId());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("핫플레이스 수정 실패", e);
        }
    }

    @Override
    public int delete(long hotplaceId) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM hotplaces WHERE hotplace_id = ?")) {
            ps.setLong(1, hotplaceId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("핫플레이스 삭제 실패", e);
        }
    }

    @Override
    public List<String> findPhotoFilesByMember(long memberId) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT photo_file FROM hotplaces WHERE member_id = ? AND photo_file IS NOT NULL")) {
            ps.setLong(1, memberId);
            List<String> files = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    files.add(rs.getString(1));
                }
            }
            return files;
        } catch (SQLException e) {
            throw new DataAccessException("핫플레이스 사진 조회 실패", e);
        }
    }

    @Override
    public Hotplace findById(long hotplaceId) {
        List<Hotplace> list = query(SELECT + " WHERE h.hotplace_id = ?", List.of(hotplaceId));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 조건에 따라 WHERE 절과 바인딩 값을 함께 만든다. 값은 전부 ? 로 바인딩한다. */
    private static String where(HotplaceCondition c, List<Object> params) {
        List<String> parts = new ArrayList<>();
        if (c.getPlaceType() != null) {
            parts.add("h.place_type = ?");
            params.add(c.getPlaceType());
        }
        if (c.getMemberId() != null) {
            parts.add("h.member_id = ?");
            params.add(c.getMemberId());
        }
        return parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts);
    }

    @Override
    public int count(HotplaceCondition c) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM hotplaces h" + where(c, params);
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("핫플레이스 수 조회 실패", e);
        }
    }

    @Override
    public List<Hotplace> search(HotplaceCondition c) {
        List<Object> params = new ArrayList<>();
        String sql = SELECT + where(c, params) + " ORDER BY h.created_at DESC, h.hotplace_id DESC LIMIT ? OFFSET ?";
        params.add(c.getItemsPerPage());
        params.add(c.getOffset());
        return query(sql, params);
    }

    private List<Hotplace> query(String sql, List<Object> params) {
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            List<Hotplace> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("핫플레이스 조회 실패", e);
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static Hotplace map(ResultSet rs) throws SQLException {
        Hotplace h = new Hotplace();
        h.setHotplaceId(rs.getLong("hotplace_id"));
        h.setMemberId(rs.getLong("member_id"));
        h.setWriterName(rs.getString("writer_name"));
        h.setTitle(rs.getString("title"));
        h.setPlaceType(rs.getString("place_type"));
        h.setVisitedOn(rs.getObject("visited_on", LocalDate.class));
        h.setDescription(rs.getString("description"));
        h.setLatitude(rs.getDouble("latitude"));
        h.setLongitude(rs.getDouble("longitude"));
        h.setPhotoFile(rs.getString("photo_file"));
        h.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return h;
    }
}
