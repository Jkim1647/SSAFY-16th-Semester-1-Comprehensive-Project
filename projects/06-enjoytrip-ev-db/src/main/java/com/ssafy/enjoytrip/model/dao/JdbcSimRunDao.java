package com.ssafy.enjoytrip.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.enjoytrip.model.dto.Reservation;
import com.ssafy.enjoytrip.model.dto.SimRunSummary;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.DataAccessException;

public class JdbcSimRunDao implements SimRunDao {

    private static final JdbcSimRunDao INSTANCE = new JdbcSimRunDao();

    private JdbcSimRunDao() {
    }

    public static JdbcSimRunDao getInstance() {
        return INSTANCE;
    }

    @Override
    public long insert(String mode, String queueType, int vehicles, int stations) {
        String sql = "INSERT INTO sim_runs (mode, queue_type, vehicles, stations, status, started_at)"
                + " VALUES (?, ?, ?, ?, 'RUNNING', NOW(3))";
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, mode);
            ps.setString(2, queueType);
            ps.setInt(3, vehicles);
            ps.setInt(4, stations);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("시뮬레이션 기록 생성 실패", e);
        }
    }

    @Override
    public void finish(long runId, String status, String queueType, int confirmed, int rejected, int overbooked) {
        String sql = "UPDATE sim_runs SET status = ?, queue_type = ?, confirmed = ?, rejected = ?, overbooked = ?,"
                + " finished_at = NOW(3) WHERE run_id = ?";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, queueType);
            ps.setInt(3, confirmed);
            ps.setInt(4, rejected);
            ps.setInt(5, overbooked);
            ps.setLong(6, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("시뮬레이션 기록 갱신 실패", e);
        }
    }

    @Override
    public List<SimRunSummary> recent(int limit) {
        String sql = "SELECT *, TIMESTAMPDIFF(MICROSECOND, started_at, finished_at) DIV 1000 AS duration_ms"
                + " FROM sim_runs ORDER BY run_id DESC LIMIT ?";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            List<SimRunSummary> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SimRunSummary s = new SimRunSummary();
                    s.setRunId(rs.getLong("run_id"));
                    s.setMode(rs.getString("mode"));
                    s.setQueueType(rs.getString("queue_type"));
                    s.setVehicles(rs.getInt("vehicles"));
                    s.setStations(rs.getInt("stations"));
                    s.setStatus(rs.getString("status"));
                    s.setConfirmed(rs.getInt("confirmed"));
                    s.setRejected(rs.getInt("rejected"));
                    s.setOverbooked(rs.getInt("overbooked"));
                    Timestamp started = rs.getTimestamp("started_at");
                    s.setStartedAt(started.toLocalDateTime());
                    long d = rs.getLong("duration_ms");
                    s.setDurationMs(rs.wasNull() ? null : d);
                    list.add(s);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("시뮬레이션 기록 조회 실패", e);
        }
    }

    @Override
    public List<Reservation> findSeats(long runId) {
        String sql = "SELECT stat_id, charger_no, vehicle_no FROM ev_reservations"
                + " WHERE run_id = ? AND status = 'CONFIRMED' ORDER BY reservation_id";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, runId);
            List<Reservation> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation r = new Reservation();
                    r.setStatId(rs.getString(1));
                    r.setChargerNo(rs.getInt(2));
                    int v = rs.getInt(3);
                    r.setVehicleNo(rs.wasNull() ? null : v);
                    list.add(r);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("좌석 조회 실패", e);
        }
    }

    @Override
    public void failStaleRuns() {
        try (Connection con = DBUtil.getConnection(); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE sim_runs SET status = 'FAILED', finished_at = NOW(3) WHERE status = 'RUNNING'");
        } catch (SQLException e) {
            throw new DataAccessException("시뮬레이션 기록 정리 실패", e);
        }
    }
}
