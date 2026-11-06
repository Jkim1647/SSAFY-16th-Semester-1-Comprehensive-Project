package com.ssafy.enjoytrip.algorithm.route;

import com.ssafy.enjoytrip.algorithm.geo.DistanceCalculator;
import com.ssafy.enjoytrip.algorithm.geo.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 여행 계획의 관광지 방문 순서 최적화 (외판원 문제, TSP).
 *
 * <ul>
 *   <li>경유지 n 개가 {@code exactLimit} 이하: Held-Karp 비트마스크 DP, O(n²·2ⁿ) 시간 / O(n·2ⁿ) 메모리, 최적해</li>
 *   <li>그보다 많으면: 최근접 이웃으로 초기 경로를 만들고 2-opt 로 교차 구간을 풀어 개선, 근사해</li>
 * </ul>
 *
 * 출발지는 항상 맨 앞에 고정되고, returnToStart 가 true 면 출발지로 돌아오는 순환 경로를 만든다.
 * 상태가 없으므로 하나의 인스턴스를 여러 스레드에서 공유해도 된다 (Spring 싱글턴 빈으로 사용 가능).
 */
public class RouteOptimizer {

    /** n=15 일 때 DP 테이블 15·2¹⁵ 칸 (double+int ≈ 6MB), 수 ms 안에 끝나는 범위 */
    public static final int DEFAULT_EXACT_LIMIT = 15;
    /** 비트마스크를 int 로 쓰고 메모리가 폭증하지 않도록 정한 상한 (n=20 ≈ 252MB) */
    public static final int MAX_EXACT_LIMIT = 20;

    private static final double EPS = 1e-9;

    private final DistanceCalculator distance;
    private final int exactLimit;

    public RouteOptimizer(DistanceCalculator distance) {
        this(distance, DEFAULT_EXACT_LIMIT);
    }

    public RouteOptimizer(DistanceCalculator distance, int exactLimit) {
        if (distance == null) throw new IllegalArgumentException("distance 는 null 일 수 없습니다.");
        if (exactLimit < 0 || exactLimit > MAX_EXACT_LIMIT) {
            throw new IllegalArgumentException("exactLimit 는 0~" + MAX_EXACT_LIMIT + " 이어야 합니다: " + exactLimit);
        }
        this.distance = distance;
        this.exactLimit = exactLimit;
    }

    public RouteResult optimize(Location start, List<? extends Location> stops, boolean returnToStart) {
        if (start == null) throw new IllegalArgumentException("출발지가 없습니다.");
        if (stops == null) throw new IllegalArgumentException("경유지 목록이 null 입니다.");
        int n = stops.size();

        List<Location> nodes = new ArrayList<Location>(n + 1);
        nodes.add(start);
        nodes.addAll(stops);
        double[][] dist = buildMatrix(nodes);

        int[] tour;
        RouteResult.Method method;
        if (n <= 1) {
            tour = n == 0 ? new int[]{0} : new int[]{0, 1};
            method = RouteResult.Method.TRIVIAL;
        } else if (n <= exactLimit) {
            tour = heldKarp(dist, returnToStart);
            method = RouteResult.Method.HELD_KARP;
        } else {
            tour = twoOpt(dist, nearestNeighbor(dist), returnToStart);
            method = RouteResult.Method.NEAREST_NEIGHBOR_2OPT;
        }

        List<Location> ordered = new ArrayList<Location>(n);
        for (int i = 1; i < tour.length; i++) ordered.add(nodes.get(tour[i]));
        return new RouteResult(start, ordered, tourLength(dist, tour, returnToStart), returnToStart, method);
    }

    double[][] buildMatrix(List<Location> nodes) {
        int size = nodes.size();
        double[][] dist = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                double d = distance.km(nodes.get(i), nodes.get(j));
                if (d < 0 || Double.isNaN(d)) {
                    throw new IllegalStateException("거리 계산 결과가 올바르지 않습니다: " + nodes.get(i) + " → " + nodes.get(j));
                }
                dist[i][j] = d;
                dist[j][i] = d;
            }
        }
        return dist;
    }

    /**
     * Held-Karp. dp[mask][j] = 출발지(0)에서 시작해 mask 에 속한 경유지를 모두 방문하고 경유지 j 에서 끝나는 최소 거리.
     * 경유지 j 는 노드 번호 j+1 에 대응한다.
     *
     * @return 0 으로 시작하는 노드 방문 순서
     */
    public static int[] heldKarp(double[][] dist, boolean returnToStart) {
        int n = dist.length - 1;
        if (n > MAX_EXACT_LIMIT) throw new IllegalArgumentException("Held-Karp 는 경유지 " + MAX_EXACT_LIMIT + "개까지만 지원합니다.");
        if (n == 0) return new int[]{0};
        int full = (1 << n) - 1;
        double[][] dp = new double[1 << n][n];
        int[][] parent = new int[1 << n][n];
        for (double[] row : dp) Arrays.fill(row, Double.POSITIVE_INFINITY);
        for (int j = 0; j < n; j++) {
            dp[1 << j][j] = dist[0][j + 1];
            parent[1 << j][j] = -1;
        }

        for (int mask = 1; mask <= full; mask++) {
            for (int last = 0; last < n; last++) {
                double cur = dp[mask][last];
                if (cur == Double.POSITIVE_INFINITY) continue;
                int remaining = full & ~mask;
                while (remaining != 0) {
                    int next = Integer.numberOfTrailingZeros(remaining);
                    remaining &= remaining - 1;
                    int nextMask = mask | (1 << next);
                    double cand = cur + dist[last + 1][next + 1];
                    if (cand < dp[nextMask][next]) {
                        dp[nextMask][next] = cand;
                        parent[nextMask][next] = last;
                    }
                }
            }
        }

        int bestLast = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int last = 0; last < n; last++) {
            double total = dp[full][last] + (returnToStart ? dist[last + 1][0] : 0);
            if (total < best) {
                best = total;
                bestLast = last;
            }
        }

        int[] tour = new int[n + 1];
        int mask = full;
        int cur = bestLast;
        for (int pos = n; pos >= 1; pos--) {
            tour[pos] = cur + 1;
            int prev = parent[mask][cur];
            mask &= ~(1 << cur);
            cur = prev;
        }
        tour[0] = 0;
        return tour;
    }

    /** 출발지(0)에서 매번 가장 가까운 미방문 지점으로 이동. O(n²) */
    public static int[] nearestNeighbor(double[][] dist) {
        int size = dist.length;
        int[] tour = new int[size];
        boolean[] visited = new boolean[size];
        visited[0] = true;
        int cur = 0;
        for (int pos = 1; pos < size; pos++) {
            int best = -1;
            for (int cand = 1; cand < size; cand++) {
                if (!visited[cand] && (best == -1 || dist[cur][cand] < dist[cur][best])) best = cand;
            }
            tour[pos] = best;
            visited[best] = true;
            cur = best;
        }
        return tour;
    }

    /**
     * 2-opt: 두 간선 (a,b), (c,d) 를 (a,c), (b,d) 로 바꿨을 때 짧아지면 사이 구간을 뒤집는다.
     * 더 이상 개선이 없을 때까지 반복. 한 바퀴 O(n²). 출발지(인덱스 0)는 고정한다.
     */
    public static int[] twoOpt(double[][] dist, int[] initialTour, boolean returnToStart) {
        int[] tour = initialTour.clone();
        int last = tour.length - 1;
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 1; i < last; i++) {
                for (int k = i + 1; k <= last; k++) {
                    int a = tour[i - 1];
                    int b = tour[i];
                    int c = tour[k];
                    double delta = dist[a][c] - dist[a][b];
                    if (k < last || returnToStart) {
                        int d = k < last ? tour[k + 1] : tour[0];
                        delta += dist[b][d] - dist[c][d];
                    }
                    if (delta < -EPS) {
                        reverse(tour, i, k);
                        improved = true;
                    }
                }
            }
        }
        return tour;
    }

    public static double tourLength(double[][] dist, int[] tour, boolean returnToStart) {
        double total = 0;
        for (int i = 1; i < tour.length; i++) total += dist[tour[i - 1]][tour[i]];
        if (returnToStart && tour.length > 1) total += dist[tour[tour.length - 1]][tour[0]];
        return total;
    }

    private static void reverse(int[] arr, int from, int to) {
        while (from < to) {
            int tmp = arr[from];
            arr[from++] = arr[to];
            arr[to--] = tmp;
        }
    }
}
