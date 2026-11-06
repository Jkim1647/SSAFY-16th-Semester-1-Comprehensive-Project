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

import com.ssafy.enjoytrip.model.dto.EvStation;
import com.ssafy.enjoytrip.model.dto.Reservation;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.DataAccessException;

public class JdbcEvDao implements EvDao {

    private static final JdbcEvDao INSTANCE = new JdbcEvDao();

    private JdbcEvDao() {
    }

    public static JdbcEvDao getInstance() {
        return INSTANCE;
    }

    @Override
    public EvStation findStation(String statId) {
        List<EvStation> list = stations("SELECT *, NULL AS dist FROM ev_stations WHERE stat_id = ?", statId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<EvStation> findNearby(double lat, double lng, double radiusKm, int minKw, int limit) {
        // 위경도 사각형으로 먼저 걸러 인덱스 없이도 계산량을 줄이고, 하버사인 거리로 정렬한다
        double dLat = radiusKm / 111.0;
        double dLng = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        String sql = "SELECT * FROM (SELECT s.*, 6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(latitude - ?) / 2), 2)"
                + " + COS(RADIANS(?)) * COS(RADIANS(latitude)) * POWER(SIN(RADIANS(longitude - ?) / 2), 2))) AS dist"
                + " FROM ev_stations s WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? AND max_kw >= ?) t"
                + " WHERE dist <= ? ORDER BY dist LIMIT ?";
        return stations(sql, lat, lat, lng, lat - dLat, lat + dLat, lng - dLng, lng + dLng, minKw, radiusKm, limit);
    }

    @Override
    public List<EvStation> searchByName(String keyword, int limit) {
        String like = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return stations("SELECT *, NULL AS dist FROM ev_stations WHERE name LIKE ? ORDER BY max_kw DESC, name LIMIT ?", like, limit);
    }

    @Override
    public List<EvStation> findAll(int minKw) {
        return stations("SELECT *, NULL AS dist FROM ev_stations WHERE max_kw >= ?", minKw);
    }

    private List<EvStation> stations(String sql, Object... params) {
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<EvStation> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EvStation s = new EvStation();
                    s.setStatId(rs.getString("stat_id"));
                    s.setName(rs.getString("name"));
                    s.setLatitude(rs.getDouble("latitude"));
                    s.setLongitude(rs.getDouble("longitude"));
                    s.setMaxKw(rs.getInt("max_kw"));
                    s.setChargerCount(rs.getInt("charger_count"));
                    double d = rs.getDouble("dist");
                    s.setDistanceKm(rs.wasNull() ? null : Math.round(d * 10) / 10.0);
                    list.add(s);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("충전소 조회 실패", e);
        }
    }

    @Override
    public void ensureSlot(Connection con, long runId, String statId, LocalDateTime slot, int capacity) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT IGNORE INTO ev_slots (run_id, stat_id, slot_start, capacity, booked) VALUES (?, ?, ?, ?, 0)")) {
            ps.setLong(1, runId);
            ps.setString(2, statId);
            ps.setTimestamp(3, Timestamp.valueOf(slot));
            ps.setInt(4, capacity);
            ps.executeUpdate();
        }
    }

    @Override
    public int[] lockSlot(Connection con, long runId, String statId, LocalDateTime slot) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT capacity, booked FROM ev_slots WHERE run_id = ? AND stat_id = ? AND slot_start = ? FOR UPDATE")) {
            ps.setLong(1, runId);
            ps.setString(2, statId);
            ps.setTimestamp(3, Timestamp.valueOf(slot));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("슬롯이 없습니다: " + statId + " " + slot);
                }
                return new int[] { rs.getInt(1), rs.getInt(2) };
            }
        }
    }

    @Override
    public void increaseBooked(Connection con, long runId, String statId, LocalDateTime slot, int delta) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE ev_slots SET booked = booked + ? WHERE run_id = ? AND stat_id = ? AND slot_start = ?")) {
            ps.setInt(1, delta);
            ps.setLong(2, runId);
            ps.setString(3, statId);
            ps.setTimestamp(4, Timestamp.valueOf(slot));
            ps.executeUpdate();
        }
    }

    @Override
    public int countConfirmed(Connection con, long runId, String statId, LocalDateTime slot) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM ev_reservations"
                + " WHERE run_id = ? AND stat_id = ? AND slot_start = ? AND status = 'CONFIRMED'")) {
            ps.setLong(1, runId);
            ps.setString(2, statId);
            ps.setTimestamp(3, Timestamp.valueOf(slot));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Override
    public long insertReservation(Connection con, Reservation r) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO ev_reservations"
                + " (run_id, stat_id, slot_start, charger_no, member_id, vehicle_no, status) VALUES (?, ?, ?, ?, ?, ?, 'CONFIRMED')",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, r.getRunId());
            ps.setString(2, r.getStatId());
            ps.setTimestamp(3, Timestamp.valueOf(r.getSlotStart()));
            ps.setInt(4, r.getChargerNo());
            ps.setObject(5, r.getMemberId());
            ps.setObject(6, r.getVehicleNo());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Reservation findReservationForUpdate(Connection con, long reservationId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT r.*, NULL AS station_name FROM ev_reservations r"
                        + " WHERE r.reservation_id = ? FOR UPDATE")) {
            ps.setLong(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    @Override
    public void updateStatus(Connection con, long reservationId, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE ev_reservations SET status = ? WHERE reservation_id = ?")) {
            ps.setString(1, status);
            ps.setLong(2, reservationId);
            ps.executeUpdate();
        }
    }

    @Override
    public boolean hasMemberReservation(Connection con, long memberId, LocalDateTime slot) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM ev_reservations"
                + " WHERE run_id = 0 AND member_id = ? AND slot_start = ? AND status = 'CONFIRMED' LIMIT 1")) {
            ps.setLong(1, memberId);
            ps.setTimestamp(2, Timestamp.valueOf(slot));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void lockMember(Connection con, long memberId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT member_id FROM members WHERE member_id = ? FOR UPDATE")) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("회원을 찾을 수 없습니다: " + memberId);
                }
            }
        }
    }

    @Override
    public int findAvailableCharger(Connection con, long runId, String statId, LocalDateTime slot, int capacity)
            throws SQLException {
        boolean[] used = new boolean[capacity + 1];
        try (PreparedStatement ps = con.prepareStatement("SELECT charger_no FROM ev_reservations"
                + " WHERE run_id = ? AND stat_id = ? AND slot_start = ? AND status = 'CONFIRMED'")) {
            ps.setLong(1, runId);
            ps.setString(2, statId);
            ps.setTimestamp(3, Timestamp.valueOf(slot));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int no = rs.getInt(1);
                    if (no > 0 && no <= capacity) {
                        used[no] = true;
                    }
                }
            }
        }
        for (int no = 1; no <= capacity; no++) {
            if (!used[no]) {
                return no;
            }
        }
        return 0;
    }

    @Override
    public List<Reservation> findConfirmed(long runId, String statId, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT r.*, s.name AS station_name FROM ev_reservations r JOIN ev_stations s ON s.stat_id = r.stat_id"
                + " WHERE r.run_id = ? AND r.stat_id = ? AND r.slot_start >= ? AND r.slot_start < ? AND r.status = 'CONFIRMED'"
                + " ORDER BY r.slot_start, r.reservation_id";
        return reservations(sql, runId, statId, Timestamp.valueOf(from), Timestamp.valueOf(to));
    }

    @Override
    public List<Reservation> findByMember(long memberId) {
        String sql = "SELECT r.*, s.name AS station_name FROM ev_reservations r JOIN ev_stations s ON s.stat_id = r.stat_id"
                + " WHERE r.run_id = 0 AND r.member_id = ? ORDER BY r.slot_start DESC LIMIT 50";
        return reservations(sql, memberId);
    }

    private List<Reservation> reservations(String sql, Object... params) {
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<Reservation> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("예약 조회 실패", e);
        }
    }

    @Override
    public int countOverbooked(long runId) {
        String sql = "SELECT COALESCE(SUM(GREATEST(c.cnt - s.capacity, 0)), 0) FROM ev_slots s JOIN ("
                + " SELECT stat_id, slot_start, COUNT(*) AS cnt FROM ev_reservations"
                + " WHERE run_id = ? AND status = 'CONFIRMED' GROUP BY stat_id, slot_start) c"
                + " ON c.stat_id = s.stat_id AND c.slot_start = s.slot_start WHERE s.run_id = ?";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, runId);
            ps.setLong(2, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("오버부킹 집계 실패", e);
        }
    }

    @Override
    public void purgeOldRuns(int keep) {
        try (Connection con = DBUtil.getConnection(); Statement st = con.createStatement()) {
            long threshold;
            try (ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(run_id), 0) FROM sim_runs")) {
                rs.next();
                threshold = rs.getLong(1) - keep;
            }
            if (threshold <= 0) {
                return;
            }
            try (PreparedStatement p1 = con.prepareStatement("DELETE FROM ev_reservations WHERE run_id BETWEEN 1 AND ?");
                    PreparedStatement p2 = con.prepareStatement("DELETE FROM ev_slots WHERE run_id BETWEEN 1 AND ?")) {
                p1.setLong(1, threshold);
                p1.executeUpdate();
                p2.setLong(1, threshold);
                p2.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DataAccessException("시뮬레이션 데이터 정리 실패", e);
        }
    }

    private static Reservation map(ResultSet rs) throws SQLException {
        Reservation r = new Reservation();
        r.setReservationId(rs.getLong("reservation_id"));
        r.setRunId(rs.getLong("run_id"));
        r.setStatId(rs.getString("stat_id"));
        r.setStationName(rs.getString("station_name"));
        r.setSlotStart(rs.getTimestamp("slot_start").toLocalDateTime());
        r.setChargerNo(rs.getInt("charger_no"));
        long member = rs.getLong("member_id");
        r.setMemberId(rs.wasNull() ? null : member);
        int vehicle = rs.getInt("vehicle_no");
        r.setVehicleNo(rs.wasNull() ? null : vehicle);
        r.setStatus(rs.getString("status"));
        r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return r;
    }
}
