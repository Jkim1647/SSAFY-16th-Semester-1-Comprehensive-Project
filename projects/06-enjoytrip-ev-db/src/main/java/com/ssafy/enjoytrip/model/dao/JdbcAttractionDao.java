package com.ssafy.enjoytrip.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ssafy.enjoytrip.model.dto.Attraction;
import com.ssafy.enjoytrip.model.dto.AttractionCondition;
import com.ssafy.enjoytrip.model.dto.CodeName;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.DataAccessException;

public class JdbcAttractionDao implements AttractionDao {

    private static final JdbcAttractionDao INSTANCE = new JdbcAttractionDao();
    private static final String SELECT = "SELECT a.*, t.name AS type_name, s.gugun_name AS signgu_name FROM attractions a"
            + " JOIN content_types t ON t.content_type_id = a.content_type_id"
            + " LEFT JOIN guguns s ON s.sido_code = a.sido_code AND s.gugun_code = a.signgu_code";

    private JdbcAttractionDao() {
    }

    public static JdbcAttractionDao getInstance() {
        return INSTANCE;
    }

    /** 조건에 따라 WHERE 절과 바인딩 값을 함께 만든다. 값은 전부 ? 로 바인딩한다. */
    private static String where(AttractionCondition c, List<Object> params) {
        List<String> parts = new ArrayList<>();
        if (c.getSignguCode() != null) {
            // 시군구 값은 sido_code * 1000 + gugun_code 로 합쳐서 온다 (구군 코드는 시도마다 겹친다)
            parts.add("a.sido_code = ? AND a.signgu_code = ?");
            params.add(c.getSignguCode() / 1000);
            params.add(c.getSignguCode() % 1000);
        }
        if (c.getContentTypeId() != null) {
            parts.add("a.content_type_id = ?");
            params.add(c.getContentTypeId());
        }
        if (c.getKeyword() != null) {
            parts.add("a.title LIKE ?");
            params.add(c.likeKeyword());
        }
        return parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts);
    }

    @Override
    public int count(AttractionCondition c) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM attractions a" + where(c, params);
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("관광지 수 조회 실패", e);
        }
    }

    @Override
    public List<Attraction> search(AttractionCondition c) {
        List<Object> params = new ArrayList<>();
        // 사진이 있는 곳을 먼저 보여준다 (관광공사 arrange=Q 와 같은 의도)
        String sql = SELECT + where(c, params)
                + " ORDER BY (a.first_image IS NULL OR a.first_image = '') , a.title, a.content_id LIMIT ? OFFSET ?";
        params.add(c.getItemsPerPage());
        params.add(c.getOffset());
        return query(sql, params);
    }

    @Override
    public Attraction findById(int contentId) {
        List<Attraction> list = query(SELECT + " WHERE a.content_id = ?", List.of(contentId));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<Attraction> findByIds(List<Integer> contentIds) {
        if (contentIds.isEmpty()) {
            return List.of();
        }
        String marks = String.join(",", Collections.nCopies(contentIds.size(), "?"));
        return query(SELECT + " WHERE a.content_id IN (" + marks + ")", new ArrayList<>(contentIds));
    }

    @Override
    public List<CodeName> findSigngu() {
        return codes("SELECT g.sido_code * 1000 + g.gugun_code, CONCAT(s.sido_name, ' ', g.gugun_name)"
                + " FROM guguns g JOIN sidos s ON s.sido_code = g.sido_code"
                + " ORDER BY s.sido_code, g.gugun_code");
    }

    @Override
    public List<CodeName> findContentTypes() {
        return codes("SELECT content_type_id, name FROM content_types ORDER BY content_type_id");
    }

    private List<CodeName> codes(String sql) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<CodeName> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new CodeName(rs.getInt(1), rs.getString(2)));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("코드 조회 실패", e);
        }
    }

    private List<Attraction> query(String sql, List<Object> params) {
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            List<Attraction> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("관광지 조회 실패", e);
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static Attraction map(ResultSet rs) throws SQLException {
        Attraction a = new Attraction();
        a.setContentId(rs.getInt("content_id"));
        a.setContentTypeId(rs.getInt("content_type_id"));
        a.setContentTypeName(rs.getString("type_name"));
        a.setTitle(rs.getString("title"));
        a.setAddr1(rs.getString("addr1"));
        a.setAddr2(rs.getString("addr2"));
        a.setTel(rs.getString("tel"));
        a.setFirstImage(rs.getString("first_image"));
        a.setFirstImage2(rs.getString("first_image2"));
        a.setLatitude(rs.getDouble("latitude"));
        a.setLongitude(rs.getDouble("longitude"));
        a.setSidoCode((Integer) rs.getObject("sido_code"));
        a.setSignguCode((Integer) rs.getObject("signgu_code"));
        a.setSignguName(rs.getString("signgu_name"));
        return a;
    }
}
