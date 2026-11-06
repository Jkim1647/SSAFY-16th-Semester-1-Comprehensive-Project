package com.ssafy.enjoytrip.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.model.dto.News;
import com.ssafy.enjoytrip.model.dto.SearchCondition;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.DataAccessException;

public class JdbcNewsDao implements NewsDao {

    private static final JdbcNewsDao INSTANCE = new JdbcNewsDao();
    private static final String COLUMNS = "news_id, keyword, title, link, source, published_at, crawled_at";

    private JdbcNewsDao() {
    }

    public static JdbcNewsDao getInstance() {
        return INSTANCE;
    }

    @Override
    public int insertIgnore(List<News> list) {
        // link 가 UNIQUE 라서 다시 수집해도 같은 기사는 0행으로 무시된다.
        // 배치(rewriteBatchedStatements)는 건별 결과를 알려 주지 않으므로 한 Connection 에서 한 건씩 실행해 새 행 수를 센다.
        String sql = "INSERT IGNORE INTO news (keyword, title, link, source, published_at) VALUES (?, ?, ?, ?, ?)";
        int saved = 0;
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (News n : list) {
                ps.setString(1, n.getKeyword());
                ps.setString(2, n.getTitle());
                ps.setString(3, n.getLink());
                ps.setString(4, n.getSource());
                ps.setObject(5, n.getPublishedAt()); // LocalDateTime → DATETIME (시간대 변환 없음)
                saved += ps.executeUpdate();
            }
            return saved;
        } catch (SQLException e) {
            throw new DataAccessException("뉴스 저장 실패", e);
        }
    }

    /** 검색어 칩은 정확히 같은 keyword 만 고른다. key 는 SearchCondition 허용 목록(keyword)을 거친다. */
    private String where(SearchCondition c) {
        return c.hasKeyword() ? " WHERE keyword = ?" : "";
    }

    @Override
    public int count(SearchCondition c) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM news" + where(c))) {
            if (c.hasKeyword()) {
                ps.setString(1, c.getWord());
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("뉴스 수 조회 실패", e);
        }
    }

    @Override
    public List<News> search(SearchCondition c) {
        // 발행 시각이 없는 기사는 수집 시각으로 정렬한다
        String sql = "SELECT " + COLUMNS + " FROM news" + where(c)
                + " ORDER BY COALESCE(published_at, crawled_at) DESC, news_id DESC LIMIT ? OFFSET ?";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            if (c.hasKeyword()) {
                ps.setString(i++, c.getWord());
            }
            ps.setInt(i++, c.getItemsPerPage());
            ps.setInt(i, c.getOffset());
            List<News> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("뉴스 목록 조회 실패", e);
        }
    }

    @Override
    public Map<String, Integer> countByKeyword() {
        String sql = "SELECT keyword, COUNT(*) AS cnt FROM news GROUP BY keyword ORDER BY MAX(crawled_at) DESC, keyword";
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            Map<String, Integer> map = new LinkedHashMap<>();
            while (rs.next()) {
                map.put(rs.getString("keyword"), rs.getInt("cnt"));
            }
            return map;
        } catch (SQLException e) {
            throw new DataAccessException("뉴스 검색어 조회 실패", e);
        }
    }

    private News map(ResultSet rs) throws SQLException {
        News n = new News(rs.getString("keyword"), rs.getString("title"), rs.getString("link"), rs.getString("source"),
                rs.getObject("published_at", LocalDateTime.class));
        n.setNewsId(rs.getLong("news_id"));
        n.setCrawledAt(rs.getObject("crawled_at", LocalDateTime.class));
        return n;
    }
}
