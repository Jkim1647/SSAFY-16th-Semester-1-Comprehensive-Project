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

import com.ssafy.enjoytrip.model.dto.Plan;
import com.ssafy.enjoytrip.model.dto.PlanStop;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.DataAccessException;

public class JdbcPlanDao implements PlanDao {

    private static final JdbcPlanDao INSTANCE = new JdbcPlanDao();

    private JdbcPlanDao() {
    }

    public static JdbcPlanDao getInstance() {
        return INSTANCE;
    }

    @Override
    public long insert(Connection con, Plan plan) throws SQLException {
        String sql = "INSERT INTO plans (member_id, title, start_date, end_date, start_soc, memo) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, plan.getMemberId());
            bindPlan(ps, plan, 2);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Override
    public int update(Connection con, Plan plan) throws SQLException {
        String sql = "UPDATE plans SET title = ?, start_date = ?, end_date = ?, start_soc = ?, memo = ?"
                + " WHERE plan_id = ? AND member_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bindPlan(ps, plan, 1);
            ps.setLong(6, plan.getPlanId());
            ps.setLong(7, plan.getMemberId());
            return ps.executeUpdate();
        }
    }

    /** title, start_date, end_date, start_soc, memo 를 from 번째 ? 부터 채운다. */
    private static void bindPlan(PreparedStatement ps, Plan plan, int from) throws SQLException {
        ps.setString(from, plan.getTitle());
        ps.setObject(from + 1, plan.getStartDate());
        ps.setObject(from + 2, plan.getEndDate());
        ps.setInt(from + 3, plan.getStartSoc());
        ps.setString(from + 4, plan.getMemo());
    }

    @Override
    public void replaceStops(Connection con, long planId, List<PlanStop> stops) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM plan_stops WHERE plan_id = ?")) {
            ps.setLong(1, planId);
            ps.executeUpdate();
        }
        if (stops.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO plan_stops (plan_id, seq, content_id, visit_date, cost, memo) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int seq = 1;
            for (PlanStop s : stops) {
                ps.setLong(1, planId);
                ps.setInt(2, seq++);
                ps.setInt(3, s.getContentId());
                ps.setObject(4, s.getVisitDate());
                ps.setInt(5, s.getCost());
                ps.setString(6, s.getMemo());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public List<Plan> findByMember(long memberId) {
        String sql = "SELECT p.*,"
                + " (SELECT COUNT(*) FROM plan_stops s WHERE s.plan_id = p.plan_id) AS stop_count,"
                + " (SELECT COALESCE(SUM(s.cost), 0) FROM plan_stops s WHERE s.plan_id = p.plan_id) AS total_cost"
                + " FROM plans p WHERE p.member_id = ? ORDER BY p.updated_at DESC, p.plan_id DESC";
        try (Connection con = DBUtil.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            List<Plan> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Plan p = mapPlan(rs);
                    p.setStopCount(rs.getInt("stop_count"));
                    p.setTotalCost(rs.getInt("total_cost"));
                    list.add(p);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("여행 계획 목록 조회 실패", e);
        }
    }

    @Override
    public Plan findById(long planId) {
        try (Connection con = DBUtil.getConnection()) {
            Plan plan;
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM plans WHERE plan_id = ?")) {
                ps.setLong(1, planId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    plan = mapPlan(rs);
                }
            }
            plan.setStops(findStops(con, planId));
            plan.setStopCount(plan.getStops().size());
            plan.setTotalCost(plan.getStops().stream().mapToInt(PlanStop::getCost).sum());
            return plan;
        } catch (SQLException e) {
            throw new DataAccessException("여행 계획 조회 실패", e);
        }
    }

    private static List<PlanStop> findStops(Connection con, long planId) throws SQLException {
        String sql = "SELECT s.*, a.title, a.latitude, a.longitude, a.addr1, t.name AS type_name"
                + " FROM plan_stops s"
                + " JOIN attractions a ON a.content_id = s.content_id"
                + " JOIN content_types t ON t.content_type_id = a.content_type_id"
                + " WHERE s.plan_id = ? ORDER BY s.seq";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, planId);
            List<PlanStop> stops = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PlanStop s = new PlanStop();
                    s.setSeq(rs.getInt("seq"));
                    s.setContentId(rs.getInt("content_id"));
                    s.setVisitDate(rs.getObject("visit_date", LocalDate.class));
                    s.setCost(rs.getInt("cost"));
                    s.setMemo(rs.getString("memo"));
                    s.setTitle(rs.getString("title"));
                    s.setLatitude(rs.getDouble("latitude"));
                    s.setLongitude(rs.getDouble("longitude"));
                    s.setAddr1(rs.getString("addr1"));
                    s.setContentTypeName(rs.getString("type_name"));
                    stops.add(s);
                }
            }
            return stops;
        }
    }

    @Override
    public int delete(long planId, long memberId) {
        try (Connection con = DBUtil.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM plans WHERE plan_id = ? AND member_id = ?")) {
            ps.setLong(1, planId);
            ps.setLong(2, memberId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("여행 계획 삭제 실패", e);
        }
    }

    private static Plan mapPlan(ResultSet rs) throws SQLException {
        Plan p = new Plan();
        p.setPlanId(rs.getLong("plan_id"));
        p.setMemberId(rs.getLong("member_id"));
        p.setTitle(rs.getString("title"));
        p.setStartDate(rs.getObject("start_date", LocalDate.class));
        p.setEndDate(rs.getObject("end_date", LocalDate.class));
        p.setStartSoc(rs.getInt("start_soc"));
        p.setMemo(rs.getString("memo"));
        p.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        p.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return p;
    }
}
