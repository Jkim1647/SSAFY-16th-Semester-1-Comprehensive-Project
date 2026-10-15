package com.ssafy.enjoytrip.algorithm.demo;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlan;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlanner;
import com.ssafy.enjoytrip.algorithm.ev.VehicleSpec;
import com.ssafy.enjoytrip.algorithm.geo.DistanceCalculator;
import com.ssafy.enjoytrip.algorithm.geo.HaversineDistance;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.route.RouteOptimizer;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlan;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlanner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 실제 공공데이터(광주·전남 관광지 1,504곳, 충전소 8,072곳)로 두 알고리즘을 실행하는 시연.
 * 실행: 프로젝트 루트에서 scripts/run.ps1 demo (또는 scripts/run.sh demo)
 */
public class AlgorithmDemo {

    /** 사용자가 여행 계획에 담은 순서 그대로 (최적화 전) */
    static final Location START = new Location("start", "광주송정역", 35.1378, 126.7932);

    static final String[] PLAN_IDS = {
            "128834",  // 죽녹원
            "126730",  // 순천만습지
            "2606207", // 목포 해상케이블카
            "128025",  // 한려해상국립공원 (오동도)
            "129024",  // 땅끝마을
            "127931",  // 순천 낙안읍성
            "575607",  // 증도(신안)
            "127869",  // 대한다원
            "126375",  // 진도 운림산방
            "127715",  // 향일암(여수)
    };

    public static void main(String[] args) throws Exception {
        Path dataDir = Paths.get(args.length > 0 ? args[0] : "data");
        List<Location> attractions = CsvDataLoader.attractions(dataDir.resolve("attractions-gwangju-jeonnam.csv"));
        List<ChargingStation> stations = CsvDataLoader.stations(dataDir.resolve("ev-stations-gwangju-jeonnam.csv"));
        System.out.printf("데이터: 관광지 %,d곳, 충전소 %,d곳%n%n", attractions.size(), stations.size());

        Map<String, Location> byId = new HashMap<String, Location>();
        for (Location a : attractions) byId.put(a.getId(), a);
        List<Location> plan = new ArrayList<Location>();
        for (String id : PLAN_IDS) {
            Location a = byId.get(id);
            if (a == null) throw new IllegalStateException("관광지 데이터에 없음: " + id);
            plan.add(a);
        }

        Location start = START;
        DistanceCalculator distance = new HaversineDistance();
        VehicleSpec vehicle = VehicleSpec.defaultSpec();
        double startSoc = 60;

        // 1) 방문 순서 최적화
        double asIs = pathKm(distance, start, plan, true);
        RouteOptimizer optimizer = new RouteOptimizer(distance);
        long t0 = System.nanoTime();
        EvTripPlan trip = new EvTripPlanner(optimizer, new EvRoutePlanner(distance))
                .plan(start, plan, true, startSoc, stations, vehicle);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("[알고리즘 1] 관광지 방문 순서 최적화 (" + trip.getRoute().getMethod() + ")");
        System.out.printf("  담은 순서   : %s → 출발지 (%.1f km)%n", names(start, plan), asIs);
        System.out.printf("  최적화 순서 : %s → 출발지 (%.1f km)%n", names(start, trip.getRoute().getOrderedStops()), trip.getRoute().getTotalKm());
        System.out.printf("  이동 거리 %.1f%% 감소%n%n", (asIs - trip.getRoute().getTotalKm()) / asIs * 100);

        System.out.printf("[알고리즘 2] 충전소 경유 계획 (배터리 %.1fkWh, 1회 충전 %.0fkm, 출발 SOC %.0f%%, 최소 잔량 %.0f%%, 목표 %.0f%%)%n",
                vehicle.getBatteryKwh(), vehicle.fullRangeKm(), startSoc, vehicle.getReserveSoc(), vehicle.getTargetSoc());
        int legNo = 1;
        for (EvRoutePlan leg : trip.getLegs()) {
            System.out.printf("  %2d. %s → %s : %.1f km, %s, 후보 충전소 %,d곳%n", legNo++, leg.getOrigin().getName(),
                    leg.getDestination().getName(), leg.getTotalKm(),
                    leg.isFeasible() ? String.format("도착 잔량 %.1f%%", leg.getArrivalSoc()) : "도달 불가", leg.getCandidateCount());
            for (EvRoutePlan.ChargeStop stop : leg.getStops()) {
                System.out.printf("      ⚡ %s (%.0fkW) %.1f%% → %.0f%%, %.0f분%n", stop.getStation().getName(),
                        stop.getStation().getMaxKw(), stop.getArriveSoc(), stop.getDepartSoc(), stop.getChargeMin());
            }
        }
        System.out.printf("%n총 %.1f km, 충전 %d회, 예상 소요 %.0f분 (주행+충전), 계산 %d ms%n",
                trip.getTotalKm(), trip.getChargeStopCount(), trip.getTotalMin(), elapsedMs);

        if (args.length > 1) {
            Path out = Paths.get(args[1]);
            WebResultWriter.write(out, start, plan, asIs, trip, vehicle, startSoc, stations, elapsedMs);
            System.out.println("지도 페이지 데이터 저장: " + out);
        }
    }

    private static double pathKm(DistanceCalculator d, Location start, List<Location> stops, boolean closed) {
        double total = 0;
        Location prev = start;
        for (Location s : stops) {
            total += d.km(prev, s);
            prev = s;
        }
        return closed ? total + d.km(prev, start) : total;
    }

    private static String names(Location start, List<Location> stops) {
        List<String> names = new ArrayList<String>(Arrays.asList(start.getName()));
        for (Location s : stops) names.add(s.getName());
        return String.join(" → ", names);
    }
}
