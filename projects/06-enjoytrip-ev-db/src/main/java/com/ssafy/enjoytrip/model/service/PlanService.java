package com.ssafy.enjoytrip.model.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlanner;
import com.ssafy.enjoytrip.algorithm.ev.VehicleSpec;
import com.ssafy.enjoytrip.algorithm.geo.DistanceCalculator;
import com.ssafy.enjoytrip.algorithm.geo.HaversineDistance;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.route.RouteOptimizer;
import com.ssafy.enjoytrip.algorithm.route.RouteResult;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlan;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlanner;
import com.ssafy.enjoytrip.model.dao.JdbcPlanDao;
import com.ssafy.enjoytrip.model.dao.PlanDao;
import com.ssafy.enjoytrip.model.dto.Attraction;
import com.ssafy.enjoytrip.model.dto.EvStation;
import com.ssafy.enjoytrip.model.dto.Plan;
import com.ssafy.enjoytrip.model.dto.PlanStop;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.DataAccessException;

/**
 * F104 여행 계획. 본인 계획만 보고 고칠 수 있고, 저장은 "계획 + 방문지 전체 교체"를 한 트랜잭션으로 묶는다.
 * 방문 순서 최적화와 충전 경로 계산은 4회차 알고리즘 모듈(RouteOptimizer, EvTripPlanner)을 그대로 호출한다.
 */
public class PlanService {

    public static final int MAX_STOPS = 30;
    public static final int MAX_DAYS = 30;
    public static final int MAX_COST = 100_000_000;
    public static final int MIN_SOC = 10;
    public static final int MAX_SOC = 100;
    /** 여행 중 경유 충전은 급속(50kW 이상)만 후보로 쓴다 (EvRoutePlanner 의 기본 가정과 같다). */
    private static final int STATION_MIN_KW = 50;

    private static final PlanService INSTANCE = new PlanService();

    private final PlanDao dao = JdbcPlanDao.getInstance();
    private final DistanceCalculator distance = new HaversineDistance();
    private final RouteOptimizer routeOptimizer = new RouteOptimizer(distance);
    private final EvRoutePlanner evRoutePlanner = new EvRoutePlanner(distance);
    /** 충전소는 수천 곳이고 거의 바뀌지 않으므로 처음 한 번만 읽어 둔다. */
    private volatile List<ChargingStation> stationCache;

    private PlanService() {
    }

    public static PlanService getInstance() {
        return INSTANCE;
    }

    public List<Plan> myPlans(long memberId) {
        return dao.findByMember(memberId);
    }

    /** 본인 계획이 아니거나 없으면 null (컨트롤러는 404 로 응답해 남의 계획이 있는지조차 알려주지 않는다). */
    public Plan findMine(long memberId, long planId) {
        Plan plan = dao.findById(planId);
        return plan == null || plan.getMemberId() != memberId ? null : plan;
    }

    /** planId 가 0 이면 새로 만들고, 아니면 본인 계획을 수정한다. @return 저장된 plan_id */
    public long save(long memberId, Plan plan) {
        plan.setMemberId(memberId);
        validate(plan);
        if (plan.getPlanId() != 0 && findMine(memberId, plan.getPlanId()) == null) {
            throw new ValidationException("수정할 수 있는 계획이 아닙니다.");
        }
        Connection con = null;
        try {
            con = DBUtil.getConnection();
            con.setAutoCommit(false);
            long planId = plan.getPlanId();
            if (planId == 0) {
                planId = dao.insert(con, plan);
            } else if (dao.update(con, plan) == 0) {
                throw new ValidationException("수정할 수 있는 계획이 아닙니다.");
            }
            dao.replaceStops(con, planId, plan.getStops());
            con.commit();
            return planId;
        } catch (SQLException e) {
            DBUtil.rollback(con);
            throw new DataAccessException("여행 계획 저장 실패", e);
        } catch (RuntimeException e) {
            DBUtil.rollback(con);
            throw e;
        } finally {
            close(con);
        }
    }

    public void delete(long memberId, long planId) {
        if (dao.delete(planId, memberId) == 0) {
            throw new ValidationException("삭제할 수 있는 계획이 아닙니다.");
        }
    }

    /** 첫 번째 관광지를 출발지로 고정하고 나머지 방문 순서를 최적화한다 (≤15곳 Held-Karp, 그 이상 최근접 이웃 + 2-opt). */
    public RouteResult optimize(List<Integer> contentIds, boolean returnToStart) {
        List<Location> points = locations(contentIds);
        return routeOptimizer.optimize(points.get(0), points.subList(1, points.size()), returnToStart);
    }

    /**
     * 구간별 충전 계획. optimizeOrder 가 false 면 사용자가 드래그로 정한 순서를 그대로 따른다.
     * @param startSoc 출발 배터리 잔량 (%). VehicleSpec·EvRoutePlanner 는 SOC 를 0~100 (%) 로 다룬다.
     */
    public EvTripPlan evTrip(List<Integer> contentIds, int startSoc, boolean returnToStart, boolean optimizeOrder) {
        validateSoc(startSoc);
        return planTrip(locations(contentIds), startSoc, returnToStart, optimizeOrder);
    }

    /** 저장된 계획의 충전 계획 (저장된 순서 그대로, 출발지로 돌아오지 않음). 방문지가 2곳 미만이면 null. */
    public EvTripPlan evTrip(Plan plan) {
        if (plan.getStops().size() < 2) {
            return null;
        }
        List<Location> points = new ArrayList<>();
        for (PlanStop s : plan.getStops()) {
            requireCoordinates(s.getTitle(), s.getLatitude(), s.getLongitude());
            points.add(new Location(String.valueOf(s.getContentId()), s.getTitle(), s.getLatitude(), s.getLongitude()));
        }
        return planTrip(points, plan.getStartSoc(), false, false);
    }

    private EvTripPlan planTrip(List<Location> points, int startSoc, boolean returnToStart, boolean optimizeOrder) {
        RouteOptimizer router = optimizeOrder ? routeOptimizer : new KeepOrder(distance);
        EvTripPlanner planner = new EvTripPlanner(router, evRoutePlanner);
        return planner.plan(points.get(0), points.subList(1, points.size()), returnToStart, startSoc, stations(),
                VehicleSpec.defaultSpec());
    }

    /** 요청한 순서대로 관광지 좌표를 읽는다. 경로 계산은 2곳 이상, 중복 없이. */
    private List<Location> locations(List<Integer> contentIds) {
        if (contentIds == null || contentIds.size() < 2) {
            throw new ValidationException("경로를 계산하려면 관광지가 2곳 이상 필요합니다.");
        }
        if (contentIds.size() > MAX_STOPS) {
            throw new ValidationException("관광지는 최대 " + MAX_STOPS + "곳까지 담을 수 있습니다.");
        }
        if (new HashSet<>(contentIds).size() != contentIds.size()) {
            throw new ValidationException("같은 관광지를 두 번 담을 수 없습니다.");
        }
        Map<Integer, Attraction> byId = new HashMap<>();
        for (Attraction a : AttractionService.getInstance().findByIds(contentIds)) {
            byId.put(a.getContentId(), a);
        }
        List<Location> points = new ArrayList<>();
        for (Integer id : contentIds) {
            Attraction a = byId.get(id);
            if (a == null) {
                throw new ValidationException("존재하지 않는 관광지가 포함되어 있습니다.");
            }
            requireCoordinates(a.getTitle(), a.getLatitude(), a.getLongitude());
            points.add(a.toLocation());
        }
        return points;
    }

    /** 관광공사 데이터 중 좌표가 0 인 곳이 있다. 그대로 넣으면 적도 부근까지 가는 경로가 나온다. */
    private static void requireCoordinates(String title, double lat, double lng) {
        if (lat == 0 || lng == 0) {
            throw new ValidationException("좌표 정보가 없는 관광지(" + title + ")는 경로 계산에 쓸 수 없습니다.");
        }
    }

    private List<ChargingStation> stations() {
        List<ChargingStation> cached = stationCache;
        if (cached == null) {
            synchronized (this) {
                if (stationCache == null) {
                    stationCache = EvService.getInstance().allStations(STATION_MIN_KW).stream()
                            .map(EvStation::toChargingStation).toList();
                }
                cached = stationCache;
            }
        }
        return cached;
    }

    // ---------------------------------------------------------------- 검증

    private void validate(Plan p) {
        String title = p.getTitle() == null ? "" : p.getTitle().strip();
        if (title.isEmpty() || length(title) > 100) {
            throw new ValidationException("제목은 1~100자로 입력하세요.");
        }
        p.setTitle(title);
        p.setMemo(blankToNull(p.getMemo()));
        if (p.getMemo() != null && length(p.getMemo()) > 1000) {
            throw new ValidationException("메모는 1000자 이내로 입력하세요.");
        }
        validateSoc(p.getStartSoc());

        LocalDate start = p.getStartDate();
        LocalDate end = p.getEndDate();
        if (end != null && start == null) {
            throw new ValidationException("시작일을 먼저 입력하세요.");
        }
        if (start != null && end != null) {
            if (end.isBefore(start)) {
                throw new ValidationException("종료일은 시작일과 같거나 이후여야 합니다.");
            }
            if (p.getDays() > MAX_DAYS) {
                throw new ValidationException("여행 기간은 " + MAX_DAYS + "일 이내로 정하세요.");
            }
        }

        List<PlanStop> stops = p.getStops();
        if (stops.isEmpty()) {
            throw new ValidationException("관광지를 1곳 이상 추가하세요.");
        }
        if (stops.size() > MAX_STOPS) {
            throw new ValidationException("관광지는 최대 " + MAX_STOPS + "곳까지 담을 수 있습니다.");
        }
        List<Integer> ids = new ArrayList<>();
        for (PlanStop s : stops) {
            ids.add(s.getContentId());
            if (s.getCost() < 0 || s.getCost() > MAX_COST) {
                throw new ValidationException("경비는 0원 이상 1억 원 이하로 입력하세요.");
            }
            s.setMemo(blankToNull(s.getMemo()));
            if (s.getMemo() != null && length(s.getMemo()) > 300) {
                throw new ValidationException("방문지 메모는 300자 이내로 입력하세요.");
            }
            LocalDate d = s.getVisitDate();
            if (d != null && start != null && (d.isBefore(start) || (end != null && d.isAfter(end)))) {
                throw new ValidationException("방문 날짜는 여행 기간 안에서 고르세요.");
            }
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ValidationException("같은 관광지를 두 번 담을 수 없습니다.");
        }
        if (AttractionService.getInstance().findByIds(ids).size() != ids.size()) {
            throw new ValidationException("존재하지 않는 관광지가 포함되어 있습니다.");
        }
    }

    private static void validateSoc(int soc) {
        if (soc < MIN_SOC || soc > MAX_SOC) {
            throw new ValidationException("출발 배터리는 " + MIN_SOC + "~" + MAX_SOC + "% 사이로 입력하세요.");
        }
    }

    /** DB 의 VARCHAR(n) 은 글자(코드 포인트) 수 기준이다. */
    private static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static void close(Connection con) {
        if (con == null) {
            return;
        }
        try {
            con.setAutoCommit(true);
            con.close();
        } catch (SQLException e) {
            System.err.println("연결 반납 실패: " + e.getMessage());
        }
    }

    /**
     * 사용자가 정한 방문 순서를 바꾸지 않는 RouteOptimizer.
     * EvTripPlanner 는 먼저 순서를 최적화한 뒤 구간별 충전 계획을 세우므로, 드래그로 정한 동선 그대로
     * 충전 경로를 보고 싶을 때는 이 객체를 넘겨 "최적화" 단계만 건너뛴다.
     */
    private static final class KeepOrder extends RouteOptimizer {

        private final DistanceCalculator distance;

        KeepOrder(DistanceCalculator distance) {
            super(distance);
            this.distance = distance;
        }

        @Override
        public RouteResult optimize(Location start, List<? extends Location> stops, boolean returnToStart) {
            List<Location> ordered = new ArrayList<>(stops);
            double km = 0;
            Location prev = start;
            for (Location next : ordered) {
                km += distance.km(prev, next);
                prev = next;
            }
            if (returnToStart && !ordered.isEmpty()) {
                km += distance.km(prev, start);
            }
            return new RouteResult(start, ordered, km, returnToStart, RouteResult.Method.TRIVIAL);
        }
    }
}
