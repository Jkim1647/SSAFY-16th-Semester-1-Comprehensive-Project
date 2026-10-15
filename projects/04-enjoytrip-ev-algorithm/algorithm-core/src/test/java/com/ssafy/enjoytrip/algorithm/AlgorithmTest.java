package com.ssafy.enjoytrip.algorithm;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlan;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlanner;
import com.ssafy.enjoytrip.algorithm.ev.VehicleSpec;
import com.ssafy.enjoytrip.algorithm.geo.DistanceCalculator;
import com.ssafy.enjoytrip.algorithm.geo.HaversineDistance;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.route.RouteOptimizer;
import com.ssafy.enjoytrip.algorithm.route.RouteResult;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlan;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 외부 라이브러리 없이 실행하는 테스트. 각 알고리즘 결과를 완전탐색 결과와 비교한다.
 * BackEnd(Spring Boot) 로 옮기면 각 메서드를 JUnit5 @Test 로 그대로 바꾸면 된다.
 */
public class AlgorithmTest {

    private static final double EPS = 1e-6;
    /** 경도 1도 = 100km 인 1차원 거리. 손으로 기대값을 계산하기 쉽게 하려는 테스트 전용 거리 */
    private static final DistanceCalculator LINE = new DistanceCalculator() {
        @Override
        public double km(Location a, Location b) {
            return Math.abs(a.getLng() - b.getLng()) * 100;
        }
    };

    private static int passed;
    private static final List<String> failures = new ArrayList<String>();

    public static void main(String[] args) {
        List<String> failed = runAll();
        System.out.println();
        System.out.println("통과 " + passed + " / 실패 " + failed.size());
        if (!failed.isEmpty()) {
            for (String f : failed) System.out.println("  - " + f);
            System.exit(1);
        }
    }

    /** 모든 검증을 실행하고 실패 목록을 돌려준다. JUnit(AlgorithmSuiteTest)과 main 이 함께 쓴다. */
    static synchronized List<String> runAll() {
        passed = 0;
        failures.clear();
        run("하버사인: 서울시청-부산시청 직선거리 약 325km", AlgorithmTest::haversineKnownDistance);
        run("TSP: Held-Karp 결과가 완전탐색 최적값과 같다 (n=1~9, 열린/닫힌 경로 400건)", AlgorithmTest::heldKarpMatchesBruteForce);
        run("TSP: 근사 경로는 모든 경유지를 한 번씩 방문하는 순열이다", AlgorithmTest::heuristicIsPermutation);
        run("TSP: 2-opt 는 최근접 이웃보다 길어지지 않는다", AlgorithmTest::twoOptNeverWorse);
        run("TSP: 경유지 수에 따라 Held-Karp / 근사 방식을 고른다", AlgorithmTest::methodSelection);
        run("TSP: 출발지는 결과에 포함되지 않고 맨 앞에 고정된다", AlgorithmTest::startIsFixed);
        run("EV: 잔량으로 갈 수 있으면 충전하지 않는다", AlgorithmTest::evDirectWhenReachable);
        run("EV: 잔량이 부족하면 충전소를 경유하고, 충전소가 없으면 불가능", AlgorithmTest::evNeedsCharger);
        run("EV: 같은 위치면 출력이 높은(빠른) 충전소를 고른다", AlgorithmTest::evPrefersFastCharger);
        run("EV: Dijkstra 결과가 완전탐색 최소 시간과 같다 (랜덤 300건)", AlgorithmTest::evMatchesBruteForce);
        run("EV: 회랑 밖·완속 충전소만 가능할 때 다시 찾고, 필터는 전체 최적을 보장하지 않음", AlgorithmTest::evCorridorFallback);
        run("여행: 앞 구간 도착 SOC 가 다음 구간 출발 SOC 로 이어진다", AlgorithmTest::tripCarriesSoc);
        run("입력 검증: 잘못된 값은 IllegalArgumentException", AlgorithmTest::invalidInputs);
        ExtendedAlgorithmTest.runAll();
        return new ArrayList<String>(failures);
    }

    // ---------------------------------------------------------------- geo

    static void haversineKnownDistance() {
        double km = HaversineDistance.greatCircleKm(37.5663, 126.9779, 35.1798, 129.0750);
        check(Math.abs(km - 325) < 5, "서울-부산 " + km);
        Location a = new Location("a", "a", 37.0, 127.0);
        Location b = new Location("b", "b", 37.1, 127.1);
        check(Math.abs(new HaversineDistance().km(a, b) - new HaversineDistance(1.0).km(a, b) * 1.3) < EPS, "우회 계수");
    }

    // ---------------------------------------------------------------- TSP

    static void heldKarpMatchesBruteForce() {
        Random rnd = new Random(42);
        for (int iter = 0; iter < 200; iter++) {
            int n = 1 + iter % 9;
            double[][] dist = randomMatrix(rnd, n + 1);
            for (boolean closed : new boolean[]{false, true}) {
                int[] tour = RouteOptimizer.heldKarp(dist, closed);
                assertPermutation(tour, n + 1);
                double got = RouteOptimizer.tourLength(dist, tour, closed);
                double expected = bruteForceTsp(dist, closed);
                check(Math.abs(got - expected) < EPS, "n=" + n + " closed=" + closed + " got=" + got + " expected=" + expected);
            }
        }
    }

    static void heuristicIsPermutation() {
        Random rnd = new Random(7);
        for (int n : new int[]{2, 3, 10, 50, 200}) {
            double[][] dist = randomMatrix(rnd, n + 1);
            for (boolean closed : new boolean[]{false, true}) {
                int[] tour = RouteOptimizer.twoOpt(dist, RouteOptimizer.nearestNeighbor(dist), closed);
                assertPermutation(tour, n + 1);
                check(tour[0] == 0, "출발지 고정");
            }
        }
    }

    static void twoOptNeverWorse() {
        Random rnd = new Random(11);
        for (int iter = 0; iter < 100; iter++) {
            double[][] dist = randomMatrix(rnd, 5 + rnd.nextInt(60));
            boolean closed = iter % 2 == 0;
            int[] nn = RouteOptimizer.nearestNeighbor(dist);
            int[] opt = RouteOptimizer.twoOpt(dist, nn, closed);
            check(RouteOptimizer.tourLength(dist, opt, closed) <= RouteOptimizer.tourLength(dist, nn, closed) + EPS, "2-opt 악화");
        }
    }

    static void methodSelection() {
        Random rnd = new Random(3);
        RouteOptimizer optimizer = new RouteOptimizer(new HaversineDistance(), 8);
        Location start = randomPoint(rnd, "start");
        check(optimizer.optimize(start, randomPoints(rnd, 0), false).getMethod() == RouteResult.Method.TRIVIAL, "0개");
        check(optimizer.optimize(start, randomPoints(rnd, 1), false).getMethod() == RouteResult.Method.TRIVIAL, "1개");
        check(optimizer.optimize(start, randomPoints(rnd, 8), false).getMethod() == RouteResult.Method.HELD_KARP, "8개");
        check(optimizer.optimize(start, randomPoints(rnd, 9), false).getMethod() == RouteResult.Method.NEAREST_NEIGHBOR_2OPT, "9개");
    }

    static void startIsFixed() {
        // 일직선 위 관광지: 출발지(0)에서 열린 경로 최적은 가까운 순서 1→2→3
        Location start = new Location("s", "출발", 35, 126.0);
        List<Location> stops = Arrays.asList(
                new Location("3", "C", 35, 126.3), new Location("1", "A", 35, 126.1), new Location("2", "B", 35, 126.2));
        RouteResult r = new RouteOptimizer(LINE).optimize(start, stops, false);
        check(r.getOrderedStops().size() == 3, "출발지 미포함");
        check(names(r.getOrderedStops()).equals("A,B,C"), "순서 " + names(r.getOrderedStops()));
        check(Math.abs(r.getTotalKm() - 30) < EPS, "거리 " + r.getTotalKm());
    }

    // ---------------------------------------------------------------- EV

    /** 100kWh, 4km/kWh → 100% 로 400km, 1% = 4km. 속도 60km/h → 1km = 1분. */
    static VehicleSpec testVehicle() {
        return new VehicleSpec(100, 4, 100, 10, 80, 60, 1.0, 0);
    }

    static void evDirectWhenReachable() {
        // 출발 SOC 90%, reserve 10% → 320km 까지 무충전. 300km 이동
        EvRoutePlan plan = new EvRoutePlanner(LINE).plan(point(0), point(3), 90,
                Collections.singletonList(station("c", 1.5, 100)), testVehicle());
        check(plan.isFeasible(), "가능해야 함");
        check(plan.getStops().isEmpty(), "충전 없음");
        check(Math.abs(plan.getArrivalSoc() - 15) < EPS, "도착 SOC " + plan.getArrivalSoc());
        check(Math.abs(plan.getTotalMin() - 300) < EPS, "시간 " + plan.getTotalMin());
    }

    static void evNeedsCharger() {
        // 출발 SOC 50% → 160km 까지. 300km 목적지, 150km 지점 충전소
        EvRoutePlanner planner = new EvRoutePlanner(LINE);
        EvRoutePlan plan = planner.plan(point(0), point(3), 50,
                Collections.singletonList(station("mid", 1.5, 100)), testVehicle());
        check(plan.isFeasible(), "가능해야 함");
        check(plan.getStops().size() == 1, "1회 충전");
        EvRoutePlan.ChargeStop stop = plan.getStops().get(0);
        check(Math.abs(stop.getArriveSoc() - 12.5) < EPS, "충전소 도착 SOC " + stop.getArriveSoc());
        // 12.5% → 80% = 67.5kWh, 100kW → 40.5분
        check(Math.abs(stop.getChargeMin() - 40.5) < EPS, "충전 시간 " + stop.getChargeMin());
        check(Math.abs(plan.getArrivalSoc() - 42.5) < EPS, "도착 SOC " + plan.getArrivalSoc());

        EvRoutePlan none = planner.plan(point(0), point(3), 50, Collections.<ChargingStation>emptyList(), testVehicle());
        check(!none.isFeasible(), "충전소 없으면 불가능");
    }

    static void evPrefersFastCharger() {
        List<ChargingStation> stations = Arrays.asList(station("slow", 1.5, 7), station("fast", 1.5, 200));
        EvRoutePlan plan = new EvRoutePlanner(LINE).plan(point(0), point(3), 50, stations, testVehicle());
        check(plan.getStops().size() == 1 && plan.getStops().get(0).getStation().getId().equals("fast"), "급속 선택");
    }

    static void evMatchesBruteForce() {
        Random rnd = new Random(2026);
        VehicleSpec v = new VehicleSpec(60, 5, 150, 10, 80, 70, 0.9, 5);
        EvRoutePlanner planner = new EvRoutePlanner(LINE, Double.POSITIVE_INFINITY, 0, 0);
        int feasibleCount = 0;
        for (int iter = 0; iter < 300; iter++) {
            double destLng = 1 + rnd.nextDouble() * 8; // 100~900km
            List<ChargingStation> stations = new ArrayList<ChargingStation>();
            int k = rnd.nextInt(8);
            for (int i = 0; i < k; i++) {
                double[] kw = {7, 50, 100, 200};
                stations.add(station("s" + i, rnd.nextDouble() * destLng * 1.2 - 0.1 * destLng, kw[rnd.nextInt(4)]));
            }
            double startSoc = 15 + rnd.nextDouble() * 85;
            EvRoutePlan plan = planner.plan(point(0), point(destLng), startSoc, stations, v);
            double expected = bruteForceEv(point(0), point(destLng), startSoc, stations, v);
            if (expected == Double.POSITIVE_INFINITY) {
                check(!plan.isFeasible(), "iter " + iter + " 불가능해야 함");
            } else {
                feasibleCount++;
                check(plan.isFeasible(), "iter " + iter + " 가능해야 함");
                check(Math.abs(plan.getTotalMin() - expected) < EPS, "iter " + iter + " got=" + plan.getTotalMin() + " expected=" + expected);
                check(plan.getArrivalSoc() >= v.getReserveSoc() - EPS, "도착 잔량 reserve 이상");
                for (EvRoutePlan.ChargeStop s : plan.getStops()) check(s.getArriveSoc() >= v.getReserveSoc() - EPS, "충전소 도착 잔량");
            }
        }
        check(feasibleCount > 50, "가능한 케이스가 너무 적음: " + feasibleCount);
    }

    static void evCorridorFallback() {
        // 회랑 폭을 0 으로 두면 경로에서 벗어난 충전소는 1차 후보에서 빠진다. 그래도 그 충전소가 유일한 해면 찾아야 한다.
        DistanceCalculator plane = new DistanceCalculator() {
            @Override
            public double km(Location a, Location b) {
                double dx = (a.getLng() - b.getLng()) * 100;
                double dy = (a.getLat() - b.getLat()) * 100;
                return Math.sqrt(dx * dx + dy * dy);
            }
        };
        EvRoutePlanner planner = new EvRoutePlanner(plane, 0, 0, 0);
        ChargingStation off = new ChargingStation("off", "off", 0.5, 1.5, 100); // 경로에서 50km 벗어남
        List<ChargingStation> candidates = planner.corridorCandidates(new Location("o", "o", 0, 0), new Location("d", "d", 0, 3),
                Collections.singletonList(off));
        check(candidates.isEmpty(), "회랑 필터에서 제외돼야 함");
        EvRoutePlan plan = planner.plan(new Location("o", "o", 0, 0), new Location("d", "d", 0, 3), 50,
                Collections.singletonList(off), testVehicle());
        check(plan.isFeasible() && plan.getStops().size() == 1, "전체 충전소로 재탐색");

        // 한계 확인: 후보 필터는 "갈 수 있는가"는 지키지만 "전체 충전소 기준 최소 시간"은 보장하지 않는다.
        // 회랑 안의 50kW 충전소로 경로가 있으면, 회랑 밖의 더 빠른 350kW 충전소는 보지 않는다.
        VehicleSpec fastCar = new VehicleSpec(100, 4, 350, 10, 80, 60, 1.0, 0);
        List<ChargingStation> two = Arrays.asList(new ChargingStation("in", "in", 0, 1.5, 50),
                new ChargingStation("out", "out", 0.4, 1.5, 350)); // 경로에서 40km 벗어남
        Location o = new Location("o", "o", 0, 0);
        Location d = new Location("d", "d", 0, 3);
        EvRoutePlan narrow = new EvRoutePlanner(plane, 0.01, 0, 0).plan(o, d, 50, two, fastCar);
        EvRoutePlan wide = new EvRoutePlanner(plane, Double.POSITIVE_INFINITY, 0, 0).plan(o, d, 50, two, fastCar);
        check(narrow.getStops().get(0).getStation().getId().equals("in"), "회랑 안 충전소 사용");
        check(wide.getStops().get(0).getStation().getId().equals("out"), "전체 탐색은 더 빠른 충전소 사용");
        check(wide.getTotalMin() < narrow.getTotalMin(), "필터 결과가 전체 최적보다 느릴 수 있음 " + narrow.getTotalMin() + " vs " + wide.getTotalMin());

        // 완속 충전소만 있어도 (1차 급속 필터에서 빠져도) 경로를 찾는다
        EvRoutePlan slowOnly = new EvRoutePlanner(LINE).plan(point(0), point(3), 50,
                Collections.singletonList(station("slow", 1.5, 7)), testVehicle());
        check(slowOnly.isFeasible() && slowOnly.getStops().size() == 1, "완속 충전소로 재탐색");
    }

    // ---------------------------------------------------------------- trip

    static void tripCarriesSoc() {
        EvTripPlanner planner = new EvTripPlanner(new RouteOptimizer(LINE), new EvRoutePlanner(LINE));
        List<Location> stops = Arrays.asList(point(2), point(1));
        EvTripPlan plan = planner.plan(point(0), stops, false, 90, Collections.<ChargingStation>emptyList(), testVehicle());
        check(plan.isFeasible(), "가능해야 함");
        check(plan.getLegs().size() == 2, "구간 2개");
        check(Math.abs(plan.getLegs().get(0).getArrivalSoc() - 65) < EPS, "1구간 도착 65%");
        check(Math.abs(plan.getLegs().get(1).getArrivalSoc() - 40) < EPS, "2구간 도착 40%");
        check(Math.abs(plan.getTotalKm() - 200) < EPS, "총 200km");

        // 잔량이 모자라면 해당 구간에서 멈추고 불가능으로 표시
        EvTripPlan fail = planner.plan(point(0), Arrays.asList(point(2), point(4)), false, 90,
                Collections.<ChargingStation>emptyList(), testVehicle());
        check(!fail.isFeasible() && fail.getLegs().size() == 2, "2구간에서 실패");
    }

    static void invalidInputs() {
        expectIae(() -> new Location("x", "x", 91, 0));
        expectIae(() -> new HaversineDistance(0.5));
        expectIae(() -> new RouteOptimizer(new HaversineDistance(), 21));
        expectIae(() -> new RouteOptimizer(new HaversineDistance()).optimize(null, Collections.<Location>emptyList(), false));
        expectIae(() -> new VehicleSpec(60, 5, 100, 80, 50, 60, 0.9, 0));
        expectIae(() -> new ChargingStation("c", "c", 35, 127, 0));
        expectIae(() -> new EvRoutePlanner(LINE).plan(point(0), point(1), 120, null, testVehicle()));
    }

    // ---------------------------------------------------------------- brute force references

    static double bruteForceTsp(double[][] dist, boolean closed) {
        int n = dist.length - 1;
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i + 1;
        double[] best = {Double.POSITIVE_INFINITY};
        permute(perm, 0, dist, closed, best);
        return n == 0 ? 0 : best[0];
    }

    private static void permute(int[] perm, int k, double[][] dist, boolean closed, double[] best) {
        if (k == perm.length) {
            int[] tour = new int[perm.length + 1];
            System.arraycopy(perm, 0, tour, 1, perm.length);
            best[0] = Math.min(best[0], RouteOptimizer.tourLength(dist, tour, closed));
            return;
        }
        for (int i = k; i < perm.length; i++) {
            swap(perm, k, i);
            permute(perm, k + 1, dist, closed, best);
            swap(perm, k, i);
        }
    }

    /** 방문하지 않은 충전소 순서를 모두 시도하는 DFS (Dijkstra 와 같은 규칙) */
    static double bruteForceEv(Location origin, Location dest, double startSoc, List<ChargingStation> stations, VehicleSpec v) {
        return dfsEv(origin, startSoc, dest, stations, new boolean[stations.size()], v);
    }

    private static double dfsEv(Location at, double departSoc, Location dest, List<ChargingStation> stations, boolean[] used, VehicleSpec v) {
        double best = Double.POSITIVE_INFINITY;
        double toDest = LINE.km(at, dest);
        if (departSoc - v.socUsed(toDest) >= v.getReserveSoc()) best = v.driveMinutes(toDest);
        for (int i = 0; i < stations.size(); i++) {
            if (used[i]) continue;
            ChargingStation s = stations.get(i);
            double km = LINE.km(at, s);
            double arrive = departSoc - v.socUsed(km);
            if (arrive < v.getReserveSoc() || arrive >= v.getTargetSoc()) continue;
            used[i] = true;
            double rest = dfsEv(s, v.getTargetSoc(), dest, stations, used, v);
            used[i] = false;
            if (rest == Double.POSITIVE_INFINITY) continue;
            double cost = v.driveMinutes(km) + v.chargeMinutes(arrive, v.getTargetSoc(), s.getMaxKw()) + v.getStopOverheadMin() + rest;
            best = Math.min(best, cost);
        }
        return best;
    }

    // ---------------------------------------------------------------- helpers

    static double[][] randomMatrix(Random rnd, int size) {
        // 실제 좌표에서 만든 대칭 거리 행렬 (삼각부등식 성립)
        List<Location> pts = randomPoints(rnd, size);
        double[][] d = new double[size][size];
        HaversineDistance h = new HaversineDistance();
        for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) d[i][j] = h.km(pts.get(i), pts.get(j));
        return d;
    }

    static List<Location> randomPoints(Random rnd, int n) {
        List<Location> list = new ArrayList<Location>();
        for (int i = 0; i < n; i++) list.add(randomPoint(rnd, "p" + i));
        return list;
    }

    static Location randomPoint(Random rnd, String id) {
        // 광주·전남 범위
        return new Location(id, id, 34.3 + rnd.nextDouble() * 1.2, 126.0 + rnd.nextDouble() * 1.8);
    }

    static Location point(double lng) {
        return new Location("p" + lng, "p" + lng, 0, lng);
    }

    static ChargingStation station(String id, double lng, double kw) {
        return new ChargingStation(id, id, 0, lng, kw);
    }

    static String names(List<Location> list) {
        StringBuilder sb = new StringBuilder();
        for (Location l : list) sb.append(sb.length() == 0 ? "" : ",").append(l.getName());
        return sb.toString();
    }

    static void assertPermutation(int[] tour, int size) {
        check(tour.length == size, "길이 " + tour.length);
        boolean[] seen = new boolean[size];
        for (int t : tour) {
            check(t >= 0 && t < size && !seen[t], "중복/범위 " + Arrays.toString(tour));
            seen[t] = true;
        }
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    interface Body {
        void run() throws Exception;
    }

    static void expectIae(Body body) {
        try {
            body.run();
        } catch (IllegalArgumentException expected) {
            return;
        } catch (Exception e) {
            throw new AssertionError("IllegalArgumentException 대신 " + e);
        }
        throw new AssertionError("예외가 발생하지 않음");
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("[PASS] " + name);
        } catch (Throwable t) {
            failures.add(name + " → " + t.getMessage());
            System.out.println("[FAIL] " + name + " → " + t.getMessage());
        }
    }
}
