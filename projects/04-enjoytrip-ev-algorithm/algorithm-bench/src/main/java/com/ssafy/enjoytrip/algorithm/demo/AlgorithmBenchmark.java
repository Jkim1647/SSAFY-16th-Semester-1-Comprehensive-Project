package com.ssafy.enjoytrip.algorithm.demo;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlan;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlanner;
import com.ssafy.enjoytrip.algorithm.ev.VehicleSpec;
import com.ssafy.enjoytrip.algorithm.geo.HaversineDistance;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.route.RouteOptimizer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 성능 측정 (정확도·시간·메모리). 실제 관광지/충전소 좌표에서 무작위로 뽑아 측정한다.
 * 시간은 JIT 워밍업 후 여러 번 실행한 중앙값.
 */
public class AlgorithmBenchmark {

    private static final HaversineDistance DIST = new HaversineDistance();

    public static void main(String[] args) throws Exception {
        Path dataDir = Paths.get(args.length > 0 ? args[0] : "data");
        List<Location> attractions = CsvDataLoader.attractions(dataDir.resolve("attractions-gwangju-jeonnam.csv"));
        List<ChargingStation> stations = CsvDataLoader.stations(dataDir.resolve("ev-stations-gwangju-jeonnam.csv"));
        System.out.println("Java " + System.getProperty("java.version") + ", " + Runtime.getRuntime().availableProcessors() + " cores");
        System.out.println();

        tspExactVsBruteForce(attractions);
        tspHeuristicQuality(attractions);
        tspHeuristicScale(attractions);
        evPlanner(attractions, stations);
    }

    /** Held-Karp 와 완전탐색(n!) 시간 비교 + DP 테이블 메모리 */
    static void tspExactVsBruteForce(List<Location> attractions) {
        System.out.println("## TSP 정확 해법: 완전탐색 O(n!) vs Held-Karp O(n²·2ⁿ)");
        System.out.println("| 경유지 n | 완전탐색 (ms) | Held-Karp (ms) | DP 테이블 메모리 |");
        System.out.println("|---:|---:|---:|---:|");
        Random rnd = new Random(1);
        for (int n = 6; n <= 16; n += 2) {
            final double[][] dist = matrix(sample(attractions, n + 1, rnd));
            double hk = medianMs(5, () -> RouteOptimizer.heldKarp(dist, true));
            String bf = n <= 10 ? String.format("%.2f", medianMs(3, () -> bruteForce(dist))) : "-";
            long bytes = (1L << n) * n * (8 + 4);
            System.out.printf("| %d | %s | %.2f | %s |%n", n, bf, hk, human(bytes));
        }
        System.out.println();
    }

    /** 근사 해법이 최적해 대비 얼마나 긴지 */
    static void tspHeuristicQuality(List<Location> attractions) {
        System.out.println("## TSP 근사 해법 품질 (최적해 대비 초과 거리, 크기별 50회 평균)");
        System.out.println("| 경유지 n | 최근접 이웃 | 최근접 이웃 + 2-opt | 2-opt 최적해 일치율 |");
        System.out.println("|---:|---:|---:|---:|");
        Random rnd = new Random(2);
        for (int n = 6; n <= 14; n += 2) {
            double nnGap = 0;
            double optGap = 0;
            int same = 0;
            int trials = 50;
            for (int t = 0; t < trials; t++) {
                double[][] dist = matrix(sample(attractions, n + 1, rnd));
                double exact = RouteOptimizer.tourLength(dist, RouteOptimizer.heldKarp(dist, true), true);
                int[] nn = RouteOptimizer.nearestNeighbor(dist);
                double nnLen = RouteOptimizer.tourLength(dist, nn, true);
                double optLen = RouteOptimizer.tourLength(dist, RouteOptimizer.twoOpt(dist, nn, true), true);
                nnGap += (nnLen / exact - 1) * 100;
                optGap += (optLen / exact - 1) * 100;
                if (optLen - exact < 1e-6) same++;
            }
            System.out.printf("| %d | +%.2f%% | +%.2f%% | %d%% |%n", n, nnGap / trials, optGap / trials, same * 100 / trials);
        }
        System.out.println();
    }

    /** 경유지가 많을 때 근사 해법 시간 */
    static void tspHeuristicScale(List<Location> attractions) {
        System.out.println("## TSP 근사 해법 시간 (최근접 이웃 + 2-opt, 거리 행렬 생성 포함)");
        System.out.println("| 경유지 n | 시간 (ms) |");
        System.out.println("|---:|---:|");
        Random rnd = new Random(3);
        final RouteOptimizer optimizer = new RouteOptimizer(DIST);
        for (int n : new int[]{20, 50, 100, 200, 500}) {
            final List<Location> pts = sample(attractions, n + 1, rnd);
            double ms = medianMs(5, () -> optimizer.optimize(pts.get(0), pts.subList(1, pts.size()), true));
            System.out.printf("| %d | %.2f |%n", n, ms);
        }
        System.out.println();
    }

    /** 충전소 경유 계획: 후보 필터에 따른 후보 수·시간·결과(최적성) 차이 */
    static void evPlanner(List<Location> attractions, final List<ChargingStation> stations) {
        final int pairs = 100;
        final int fullPairs = 20; // 필터를 끈 탐색은 쌍당 수 초가 걸려 앞 20쌍만 1회씩 측정
        System.out.println("## 충전소 경유 경로 (Dijkstra O(V²)), 실제 충전소 " + stations.size()
                + "곳, 출발 SOC 20%, 무작위 관광지 출발·도착 " + pairs + "쌍");
        final VehicleSpec vehicle = VehicleSpec.defaultSpec();
        EvRoutePlanner[] planners = {
                new EvRoutePlanner(DIST),                                   // 회랑 + 급속(50kW↑) 필터 (기본값)
                new EvRoutePlanner(DIST, EvRoutePlanner.DEFAULT_DETOUR_RATIO, EvRoutePlanner.DEFAULT_MIN_DETOUR_KM, 0), // 회랑만
                new EvRoutePlanner(DIST, Double.POSITIVE_INFINITY, 0, 0),   // 필터 없음 (기준 최적해)
        };
        String[] labels = {"회랑 + 급속 필터 (기본)", "회랑 필터만", "필터 없음 (전체 충전소)"};
        List<List<Double>> times = new ArrayList<List<Double>>();
        long[] candSum = new long[3];
        int[] runs = new int[3];
        int[] same = new int[2];
        double[] maxDiff = new double[2];
        int feasible = 0;
        int withStops = 0;
        for (int p = 0; p < 3; p++) times.add(new ArrayList<Double>());

        Random rnd = new Random(4);
        for (int i = 0; i < pairs; i++) {
            final Location o = attractions.get(rnd.nextInt(attractions.size()));
            final Location d = attractions.get(rnd.nextInt(attractions.size()));
            EvRoutePlan[] result = new EvRoutePlan[3];
            for (int p = 0; p < 3; p++) {
                if (p > 0 && i >= fullPairs) break;
                final EvRoutePlanner planner = planners[p];
                final EvRoutePlan[] holder = new EvRoutePlan[1];
                times.get(p).add(p > 0 ? onceMs(() -> holder[0] = planner.plan(o, d, 20, stations, vehicle))
                        : medianMs(3, () -> holder[0] = planner.plan(o, d, 20, stations, vehicle)));
                result[p] = holder[0];
                candSum[p] += holder[0].getCandidateCount();
                runs[p]++;
            }
            if (result[0].isFeasible()) feasible++;
            if (!result[0].getStops().isEmpty()) withStops++;
            if (result[2] == null) continue;
            for (int p = 0; p < 2; p++) {
                double diff = result[p].isFeasible() != result[2].isFeasible() ? 100
                        : !result[2].isFeasible() ? 0
                        : (result[p].getTotalMin() - result[2].getTotalMin()) / result[2].getTotalMin() * 100;
                if (Math.abs(diff) < 1e-6) same[p]++;
                maxDiff[p] = Math.max(maxDiff[p], diff);
            }
        }
        System.out.println("| 방식 | 측정 쌍 | 평균 후보 수 | 중앙값 (ms) | 최댓값 (ms) | 최적해와 같은 비율 | 최대 손해 |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|");
        for (int p = 0; p < 3; p++) {
            String sameText = p == 2 ? "기준" : String.format("%d/%d", same[p], fullPairs);
            String diffText = p == 2 ? "-" : String.format("+%.2f%%", maxDiff[p]);
            System.out.printf("| %s | %d | %,d | %.2f | %.2f | %s | %s |%n", labels[p], runs[p], candSum[p] / runs[p],
                    median(times.get(p)), Collections.max(times.get(p)), sameText, diffText);
        }
        System.out.printf("%n도달 가능 %d/%d쌍, 그중 충전이 필요한 쌍 %d%n", feasible, pairs, withStops);
        long dijkstraBytes = (long) (stations.size() + 2) * (8 + 8 + 4 + 1);
        System.out.println("Dijkstra 작업 배열 메모리 (전체 충전소 기준): " + human(dijkstraBytes) + " — 간선을 저장하지 않으므로 O(V)");
        System.out.println();
    }

    static double onceMs(Task task) {
        long t0 = System.nanoTime();
        task.run();
        return (System.nanoTime() - t0) / 1e6;
    }

    // ---------------------------------------------------------------- helpers

    interface Task {
        Object run();
    }

    static double medianMs(int repeat, Task task) {
        for (int i = 0; i < 2; i++) task.run(); // 워밍업
        List<Double> times = new ArrayList<Double>();
        for (int i = 0; i < repeat; i++) {
            long t0 = System.nanoTime();
            task.run();
            times.add((System.nanoTime() - t0) / 1e6);
        }
        return median(times);
    }

    static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<Double>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    static List<Location> sample(List<Location> all, int n, Random rnd) {
        List<Location> copy = new ArrayList<Location>(all);
        Collections.shuffle(copy, rnd);
        return new ArrayList<Location>(copy.subList(0, n));
    }

    static double[][] matrix(List<Location> pts) {
        double[][] d = new double[pts.size()][pts.size()];
        for (int i = 0; i < pts.size(); i++) for (int j = 0; j < pts.size(); j++) d[i][j] = DIST.km(pts.get(i), pts.get(j));
        return d;
    }

    static double bruteForce(double[][] dist) {
        int n = dist.length - 1;
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i + 1;
        double best = Double.POSITIVE_INFINITY;
        // 사전순 다음 순열로 n! 개를 모두 검사
        do {
            int[] tour = new int[n + 1];
            System.arraycopy(perm, 0, tour, 1, n);
            best = Math.min(best, RouteOptimizer.tourLength(dist, tour, true));
        } while (nextPermutation(perm));
        return best;
    }

    static boolean nextPermutation(int[] a) {
        int i = a.length - 2;
        while (i >= 0 && a[i] >= a[i + 1]) i--;
        if (i < 0) return false;
        int j = a.length - 1;
        while (a[j] <= a[i]) j--;
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
        Arrays.sort(a, i + 1, a.length);
        return true;
    }

    static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
