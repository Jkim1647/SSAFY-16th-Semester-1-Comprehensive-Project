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
            seedSidos(con);
            seedGuguns(con);
            seedAttractions(con);
            seedSsafyAttractions(con);
            upgradeAttractionIndexes(con);
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

    /** 싸피 제공 시도 코드 (guguns 가 참조하므로 먼저 적재한다). */
    private void seedSidos(Connection con) throws SQLException {
        if (!isEmpty(con, "sidos")) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO sidos (sido_code, sido_name) VALUES (?, ?)")) {
            for (Map<String, String> r : Csv.readResource("data/sidos.csv")) {
                ps.setInt(1, Integer.parseInt(r.get("sidoCode")));
                ps.setString(2, r.get("sidoName"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** 싸피 제공 구군 코드. 같은 구군 코드가 시도마다 다시 나오므로 (sido_code, gugun_code) 쌍이 키다. */
    private void seedGuguns(Connection con) throws SQLException {
        if (!isEmpty(con, "guguns")) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO guguns (sido_code, gugun_code, gugun_name) VALUES (?, ?, ?)")) {
            for (Map<String, String> r : Csv.readResource("data/guguns.csv")) {
                ps.setInt(1, Integer.parseInt(r.get("sidoCode")));
                ps.setInt(2, Integer.parseInt(r.get("gugunCode")));
                ps.setString(3, r.get("gugunName"));
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

    private static final String SSAFY_SEED_KEY = "ssafy_attractions";
    /** 이 수 이상이면 이미 싸피 데이터가 들어 있다고 본다 (예: enjoytrip_init.sql 로 넣은 DB). */
    private static final int SSAFY_ATTRACTION_MIN = 50_000;

    /**
     * 싸피가 제공한 관광지(약 5만 곳)를 넣는다. 한 번 넣으면 표시를 남기고, 이미 5만 곳 이상이어도 다시 넣지 않는다.
     * 예전 구조의 attractions 는 이때 새 구조로 바꾼다. content_id 가 겹치는 기존 행은 싸피 값으로 갱신하되 lcls1~3 은 지킨다.
     */
    private void seedSsafyAttractions(Connection con) throws SQLException {
        if (hasSeedMark(con, SSAFY_SEED_KEY)) {
            return;
        }
        upgradeAttractionsSchema(con);
        if (countRows(con, "attractions") < SSAFY_ATTRACTION_MIN) {
            loadSsafyAttractions(con);
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT IGNORE INTO data_seed_state (seed_key) VALUES (?)")) {
            ps.setString(1, SSAFY_SEED_KEY);
            ps.executeUpdate();
        }
    }

    private boolean hasSeedMark(Connection con, String key) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM data_seed_state WHERE seed_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int countRows(Connection con, String table) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 대리키 no 가 없는 예전 attractions 를 새 구조로 바꾼다 (CREATE TABLE IF NOT EXISTS 는 이미 있는 테이블을 바꾸지 않는다). */
    private void upgradeAttractionsSchema(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM information_schema.COLUMNS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attractions' AND COLUMN_NAME = 'no'");
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return;
            }
        }
        System.out.println("[EnjoyTrip] attractions 구조를 새 구조로 바꿉니다 (no PK, content_id UNIQUE, overview 등)");
        try (Statement st = con.createStatement()) {
            // plan_stops 의 외래 키가 content_id 를 쓰므로 UNIQUE 를 먼저 만든 뒤 PK 를 바꾼다
            st.execute("ALTER TABLE attractions ADD UNIQUE KEY uk_attractions_content (content_id)");
            st.execute("ALTER TABLE attractions DROP PRIMARY KEY, ADD COLUMN `no` INT NOT NULL AUTO_INCREMENT FIRST, ADD PRIMARY KEY (`no`)");
            st.execute("ALTER TABLE attractions MODIFY latitude DECIMAL(20,17) NOT NULL, MODIFY longitude DECIMAL(20,17) NOT NULL,"
                    + " ADD COLUMN overview VARCHAR(10000) NULL, ADD COLUMN homepage VARCHAR(1000) NULL, ADD COLUMN map_level INT NULL");
        }
    }

    /**
     * 이미 만들어진 DB 에도 조회용 인덱스를 맞춘다 (CREATE TABLE IF NOT EXISTS 는 기존 테이블의 인덱스를 바꾸지 않는다).
     * content_type_id FK 가 쓸 인덱스가 항상 있도록 새 인덱스를 먼저 만들고 예전 인덱스를 지운다.
     */
    private void upgradeAttractionIndexes(Connection con) throws SQLException {
        if (hasIndex(con, "attractions", "idx_attractions_list")) {
            return;
        }
        System.out.println("[EnjoyTrip] attractions 조회 인덱스를 새 구성으로 바꿉니다");
        try (Statement st = con.createStatement()) {
            st.execute("ALTER TABLE attractions"
                    + " ADD KEY idx_attractions_region_type (sido_code, signgu_code, content_type_id),"
                    + " ADD KEY idx_attractions_type_list (content_type_id, ((first_image IS NULL OR first_image = '')), title, content_id),"
                    + " ADD KEY idx_attractions_list (((first_image IS NULL OR first_image = '')), title, content_id)");
            for (String old : new String[] { "idx_attractions_region", "idx_attractions_type" }) {
                if (hasIndex(con, "attractions", old)) {
                    st.execute("ALTER TABLE attractions DROP KEY " + old);
                }
            }
        }
    }

    private boolean hasIndex(Connection con, String table, String index) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM information_schema.STATISTICS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? LIMIT 1")) {
            ps.setString(1, table);
            ps.setString(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void loadSsafyAttractions(Connection con) throws SQLException {
        long t0 = System.currentTimeMillis();
        String sql = "INSERT INTO attractions (content_id, content_type_id, title, addr1, addr2, tel, first_image, first_image2,"
                + " latitude, longitude, sido_code, signgu_code, overview, homepage, map_level)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE content_type_id = VALUES(content_type_id), title = VALUES(title),"
                + " addr1 = VALUES(addr1), addr2 = VALUES(addr2), tel = VALUES(tel), first_image = VALUES(first_image),"
                + " first_image2 = VALUES(first_image2), latitude = VALUES(latitude), longitude = VALUES(longitude),"
                + " sido_code = VALUES(sido_code), signgu_code = VALUES(signgu_code), overview = VALUES(overview),"
                + " homepage = VALUES(homepage), map_level = VALUES(map_level)";
        con.setAutoCommit(false);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int[] n = { 0 };
            Csv.forEachResource("data/attractions-ssafy.csv.gz", r -> {
                try {
                    int i = 1;
                    ps.setInt(i++, Integer.parseInt(r.get("contentId")));
                    ps.setInt(i++, Integer.parseInt(r.get("contentTypeId")));
                    ps.setString(i++, r.get("title"));
                    ps.setString(i++, nullIfEmpty(r.get("addr1")));
                    ps.setString(i++, nullIfEmpty(r.get("addr2")));
                    ps.setString(i++, nullIfEmpty(r.get("tel")));
                    ps.setString(i++, nullIfEmpty(r.get("firstImage")));
                    ps.setString(i++, nullIfEmpty(r.get("firstImage2")));
                    ps.setBigDecimal(i++, new BigDecimal(r.get("lat")));
                    ps.setBigDecimal(i++, new BigDecimal(r.get("lng")));
                    ps.setObject(i++, parseIntOrNull(r.get("sidoCode")));
                    ps.setObject(i++, parseIntOrNull(r.get("signguCode")));
                    ps.setString(i++, nullIfEmpty(r.get("overview")));
                    ps.setString(i++, nullIfEmpty(r.get("homepage")));
                    ps.setObject(i++, parseIntOrNull(r.get("mapLevel")));
                    ps.addBatch();
                    if (++n[0] % 1000 == 0) {
                        ps.executeBatch();
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException("싸피 관광지 적재 실패", e);
                }
            });
            ps.executeBatch();
            con.commit();
            System.out.printf("[EnjoyTrip] 싸피 관광지 %d곳 적재 (%d ms)%n", n[0], System.currentTimeMillis() - t0);
        } catch (RuntimeException | SQLException e) {
            DBUtil.rollback(con);
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
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
