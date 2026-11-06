package com.ssafy.enjoytrip.listener;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.config.AppConfig;
import com.ssafy.enjoytrip.model.dao.JdbcSimRunDao;
import com.ssafy.enjoytrip.simulation.SimulationEngine;
import com.ssafy.enjoytrip.util.Csv;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.Passwords;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * 앱이 뜰 때 한 번: 테이블을 만들고(CREATE IF NOT EXISTS), 비어 있는 기준 데이터만 CSV 에서 채운다.
 * 이미 데이터가 있으면 건드리지 않으므로 재시작해도 회원·게시글이 지워지지 않는다.
 */
@WebListener
public class AppInitListener implements ServletContextListener {

    /** 실행 순서대로. 외래 키가 앞 파일의 테이블을 참조한다. */
    private static final String[] SCHEMA_FILES = {
            "sql/01-core.sql", "sql/10-ev.sql", "sql/20-plan.sql", "sql/30-hotplace.sql", "sql/40-board.sql", "sql/45-board-community.sql", "sql/50-news.sql" };

    private static final Object[][] CONTENT_TYPES = {
            { 12, "관광지" }, { 14, "문화시설" }, { 15, "축제공연행사" }, { 25, "여행코스" },
            { 28, "레포츠" }, { 32, "숙박" }, { 38, "쇼핑" }, { 39, "음식점" } };

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        long t0 = System.currentTimeMillis();
        try (Connection con = DBUtil.getConnection()) {
            for (String file : SCHEMA_FILES) {
                runScript(con, file);
            }
            seedContentTypes(con);
            seedSigngu(con);
            seedAttractions(con);
            seedStations(con);
            seedAdmin(con);
            BoardSeed.seed(con);
            BoardSeed.seedDetails(con);
            JdbcSimRunDao.getInstance().failStaleRuns();
        } catch (SQLException e) {
            throw new IllegalStateException("DB 초기화 실패 — MySQL 이 켜져 있는지, 계정(db.user/db.password)이 맞는지 확인하세요: " + e.getMessage(), e);
        }
        System.out.printf("[EnjoyTrip] DB 준비 완료 (%d ms)%n", System.currentTimeMillis() - t0);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        SimulationEngine.getInstance().shutdown();
    }

    private void runScript(Connection con, String path) throws SQLException {
        String sql;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("스키마 파일이 없습니다: " + path);
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(path, e);
        }
        try (Statement st = con.createStatement()) {
            for (String stmt : sql.replaceAll("(?m)--.*$", "").split(";")) {
                if (!stmt.isBlank()) {
                    st.execute(stmt);
                }
            }
        }
    }

    private boolean isEmpty(Connection con, String table) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT 1 FROM " + table + " LIMIT 1")) {
            return !rs.next();
        }
    }

    private void seedContentTypes(Connection con) throws SQLException {
        if (!isEmpty(con, "content_types")) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO content_types (content_type_id, name) VALUES (?, ?)")) {
            for (Object[] t : CONTENT_TYPES) {
                ps.setInt(1, (Integer) t[0]);
                ps.setString(2, (String) t[1]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void seedSigngu(Connection con) throws SQLException {
        if (!isEmpty(con, "signgu")) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO signgu (sido_code, signgu_code, name) VALUES (?, ?, ?)")) {
            for (Map<String, String> r : Csv.readResource("data/signgu.csv")) {
                ps.setInt(1, Integer.parseInt(r.get("sidoCode")));
                ps.setInt(2, Integer.parseInt(r.get("signguCode")));
                ps.setString(3, r.get("name"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void seedAttractions(Connection con) throws SQLException {
        if (!isEmpty(con, "attractions")) {
            return;
        }
        List<Map<String, String>> rows = Csv.readResource("data/attractions.csv");
        String sql = "INSERT INTO attractions (content_id, content_type_id, title, addr1, addr2, tel, first_image, first_image2,"
                + " latitude, longitude, sido_code, signgu_code, lcls1, lcls2, lcls3) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        con.setAutoCommit(false);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (Map<String, String> r : rows) {
                int i = 1;
                ps.setInt(i++, Integer.parseInt(r.get("contentId")));
                ps.setInt(i++, Integer.parseInt(r.get("contentTypeId")));
                ps.setString(i++, r.get("title"));
                ps.setString(i++, r.get("addr1"));
                ps.setString(i++, r.get("addr2"));
                ps.setString(i++, r.get("tel"));
                ps.setString(i++, r.get("firstImage"));
                ps.setString(i++, r.get("firstImage2"));
                ps.setBigDecimal(i++, new BigDecimal(r.get("lat")));
                ps.setBigDecimal(i++, new BigDecimal(r.get("lng")));
                ps.setObject(i++, parseIntOrNull(r.get("sidoCode")));
                ps.setObject(i++, parseIntOrNull(r.get("signguCode")));
                ps.setString(i++, r.get("lcls1"));
                ps.setString(i++, r.get("lcls2"));
                ps.setString(i++, r.get("lcls3"));
                ps.addBatch();
            }
            ps.executeBatch();
            con.commit();
        } catch (SQLException e) {
            DBUtil.rollback(con);
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
        System.out.println("[EnjoyTrip] 관광지 " + rows.size() + "곳 적재");
    }

    private void seedStations(Connection con) throws SQLException {
        if (!isEmpty(con, "ev_stations")) {
            return;
        }
        List<Map<String, String>> rows = Csv.readResource("data/ev-stations-gwangju-jeonnam.csv");
        con.setAutoCommit(false);
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT IGNORE INTO ev_stations (stat_id, name, latitude, longitude, max_kw, charger_count) VALUES (?,?,?,?,?,?)")) {
            for (Map<String, String> r : rows) {
                ps.setString(1, r.get("statId"));
                ps.setString(2, r.get("statNm"));
                ps.setBigDecimal(3, new BigDecimal(r.get("lat")));
                ps.setBigDecimal(4, new BigDecimal(r.get("lng")));
                ps.setInt(5, (int) Double.parseDouble(r.get("maxKw")));
                ps.setInt(6, Integer.parseInt(r.get("chargers")));
                ps.addBatch();
            }
            ps.executeBatch();
            con.commit();
        } catch (SQLException e) {
            DBUtil.rollback(con);
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
        System.out.println("[EnjoyTrip] 충전소 " + rows.size() + "곳 적재");
    }

    private void seedAdmin(Connection con) throws SQLException {
        String email = AppConfig.get("admin.email");
        String password = AppConfig.get("admin.password");
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT IGNORE INTO members (email, name, password_hash, role) VALUES (?, '관리자', ?, 'ADMIN')")) {
            ps.setString(1, email);
            ps.setString(2, Passwords.hash(password));
            if (ps.executeUpdate() == 1) {
                System.out.println("[EnjoyTrip] 관리자 계정 생성: " + email);
            }
        }
    }

    private static Integer parseIntOrNull(String s) {
        return s == null || s.isBlank() ? null : Integer.valueOf(s);
    }
}
