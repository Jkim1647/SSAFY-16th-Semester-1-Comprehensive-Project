package com.ssafy.enjoytrip.model.service;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlan;
import com.ssafy.enjoytrip.algorithm.ev.VehicleSpec;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlan;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlanner;
import com.ssafy.enjoytrip.model.dao.AttractionDao;
import com.ssafy.enjoytrip.model.dao.ChargingStationDao;
import com.ssafy.enjoytrip.model.dto.AttractionDto;
import com.ssafy.enjoytrip.model.dto.TripDtos.Charge;
import com.ssafy.enjoytrip.model.dto.TripDtos.Leg;
import com.ssafy.enjoytrip.model.dto.TripDtos.PlanRequest;
import com.ssafy.enjoytrip.model.dto.TripDtos.PlanResponse;
import com.ssafy.enjoytrip.model.dto.TripDtos.Stop;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 여행 계획: #1 방문 순서 최적화 + #2 구간별 충전소 경유 계획 */
@Service
public class TripPlanService {

    public static final int MAX_STOPS = 30;
    static final double DEFAULT_START_SOC = 60;

    private final EvTripPlanner planner;
    private final AttractionDao attractionDao;
    private final List<ChargingStation> stations;

    public TripPlanService(EvTripPlanner planner, AttractionDao attractionDao, ChargingStationDao stationDao) {
        this.planner = planner;
        this.attractionDao = attractionDao;
        this.stations = stationDao.findAll();
    }

    public PlanResponse plan(PlanRequest req) {
        if (req == null || req.start() == null || req.start().lat() == null || req.start().lng() == null) {
            throw new IllegalArgumentException("출발지 좌표(start.lat, start.lng)가 필요합니다.");
        }
        if (req.attractionIds() == null || req.attractionIds().isEmpty()) throw new IllegalArgumentException("방문할 관광지를 1곳 이상 고르세요.");
        // 같은 관광지를 두 번 넣으면 하나로 본다
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(req.attractionIds()));
        if (ids.size() > MAX_STOPS) throw new IllegalArgumentException("관광지는 " + MAX_STOPS + "곳까지 고를 수 있습니다.");
        List<AttractionDto> stops = new ArrayList<>();
        for (String id : ids) {
            stops.add(attractionDao.findById(id).orElseThrow(() -> new IllegalArgumentException("없는 관광지입니다: " + id)));
        }
        String startName = req.start().name() == null || req.start().name().isBlank() ? "출발지" : req.start().name();
        Location start = new Location("start", startName, req.start().lat(), req.start().lng());
        boolean returnToStart = req.returnToStart() == null || req.returnToStart();
        double startSoc = req.startSoc() == null ? DEFAULT_START_SOC : req.startSoc();

        long t0 = System.nanoTime();
        EvTripPlan trip = planner.plan(start, stops, returnToStart, startSoc, stations, VehicleSpec.defaultSpec());
        double ms = (System.nanoTime() - t0) / 1e6;

        List<Stop> order = trip.getRoute().getOrderedStops().stream().map(TripPlanService::stop).toList();
        List<Leg> legs = trip.getLegs().stream().map(TripPlanService::leg).toList();
        return new PlanResponse(trip.isFeasible(), trip.getRoute().getMethod().name(), stop(start), order,
                trip.getRoute().getTotalKm(), trip.getTotalKm(), trip.getTotalMin(), trip.getChargeStopCount(), ms, legs);
    }

    private static Stop stop(Location l) {
        return new Stop(l.getId(), l.getName(), l.getLat(), l.getLng());
    }

    private static Leg leg(EvRoutePlan p) {
        List<Charge> charges = p.getStops().stream().map(s -> new Charge(s.getStation().getName(), s.getStation().getLat(),
                s.getStation().getLng(), s.getStation().getMaxKw(), s.getArriveSoc(), s.getDepartSoc(), s.getChargeMin())).toList();
        // 도달 불가 구간의 수치는 NaN 이라 JSON 에 넣을 수 없으므로 null 로 보낸다
        return new Leg(p.getOrigin().getName(), p.getDestination().getName(), p.isFeasible(),
                finite(p.getTotalKm()), finite(p.getDriveMin()), finite(p.getChargeMin()), finite(p.getArrivalSoc()),
                p.getCandidateCount(), charges);
    }

    private static Double finite(double v) {
        return Double.isFinite(v) ? v : null;
    }
}
