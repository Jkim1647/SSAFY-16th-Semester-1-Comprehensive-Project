package com.ssafy.enjoytrip.algorithm.ev;

import com.ssafy.enjoytrip.algorithm.geo.DistanceCalculator;
import com.ssafy.enjoytrip.algorithm.geo.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 배터리 잔량을 고려해 충전소를 경유하는 최소 시간 경로 (Dijkstra).
 *
 * <p>그래프 모델
 * <ul>
 *   <li>정점: 출발지, 후보 충전소, 도착지</li>
 *   <li>간선 u→v: u 를 떠날 때 SOC 로 v 까지 가도 잔량이 reserveSoc 이상 남으면 존재 (모든 쌍을 암묵적으로 검사)</li>
 *   <li>가중치: 주행 시간 + (v 가 충전소면) targetSoc 까지 충전 시간 + 1회 고정 소요 시간</li>
 * </ul>
 * 충전소에서는 항상 targetSoc 까지 충전하고 떠나므로, 충전소를 떠나는 SOC 는 경로와 무관하게 일정하다.
 * 따라서 간선 가중치가 (u, v) 만으로 정해지고 모두 0 이상이라 Dijkstra 로 최적해를 구할 수 있다.
 * 단, 최적은 <b>탐색한 후보 충전소 집합 안에서</b>의 최적이다. 기본 설정은 아래 필터로 후보를 줄이므로
 * 필터에서 빠진 충전소를 거치는 더 빠른 경로를 놓칠 수 있다. 전체 충전소 기준 최적이 필요하면
 * {@code new EvRoutePlanner(distance, Double.POSITIVE_INFINITY, 0, 0)} 으로 필터를 끈다 (충전소 8천여 곳에서 쌍당 수 초).
 * 도착 시점에 이미 targetSoc 이상이면 그 충전소에 들를 이유가 없으므로 간선을 만들지 않는다.
 *
 * <p>간선이 최대 V² 개인 조밀 그래프라 힙 대신 배열로 최솟값을 찾는 O(V²) 구현을 쓴다 (힙은 O(V² log V)).
 * 탐색 전에 (1) 출발지-도착지 회랑 밖의 충전소와 (2) 출력이 minChargerKw 미만인 완속 충전소를 걸러 V 를 줄인다.
 * 걸러진 후보로 경로가 없을 때만 회랑 안 전체 → 전체 충전소 순으로 넓혀 다시 찾으므로,
 * 필터 때문에 "갈 수 있는데 못 간다"는 결과는 나오지 않는다.
 * 다만 넓힌 탐색은 느리므로(전체 8천여 곳 기준 수 초), 출발지에서 첫 충전소나 마지막 충전소에서 도착지까지
 * 닿을 수 없는 명백한 불가능 입력은 O(V) 사전 검사로 바로 돌려보낸다.
 */
public class EvRoutePlanner {

    public static final double DEFAULT_DETOUR_RATIO = 0.3;
    public static final double DEFAULT_MIN_DETOUR_KM = 20;
    /** 여행 중 경유 충전은 급속(50kW 이상) 위주로 한다는 가정 */
    public static final double DEFAULT_MIN_CHARGER_KW = 50;

    private final DistanceCalculator distance;
    private final double detourRatio;
    private final double minDetourKm;
    private final double minChargerKw;

    public EvRoutePlanner(DistanceCalculator distance) {
        this(distance, DEFAULT_DETOUR_RATIO, DEFAULT_MIN_DETOUR_KM, DEFAULT_MIN_CHARGER_KW);
    }

    /**
     * @param detourRatio 회랑 폭: 출발지→충전소→도착지 거리가 직행 거리보다 max(직행·detourRatio, minDetourKm) 이상 길면 후보에서 뺀다.
     *                    {@code Double.POSITIVE_INFINITY} 면 필터를 끈다.
     * @param minChargerKw 1차 탐색에 쓸 최소 충전 출력 (kW). 0 이면 필터를 끈다.
     */
    public EvRoutePlanner(DistanceCalculator distance, double detourRatio, double minDetourKm, double minChargerKw) {
        if (distance == null) throw new IllegalArgumentException("distance 는 null 일 수 없습니다.");
        if (!(detourRatio >= 0) || !(minDetourKm >= 0)) throw new IllegalArgumentException("회랑 폭은 0 이상이어야 합니다.");
        if (!(minChargerKw >= 0)) throw new IllegalArgumentException("최소 충전 출력은 0 이상이어야 합니다.");
        this.distance = distance;
        this.detourRatio = detourRatio;
        this.minDetourKm = minDetourKm;
        this.minChargerKw = minChargerKw;
    }

    public EvRoutePlan plan(Location origin, Location destination, double startSoc,
                            List<? extends ChargingStation> stations, VehicleSpec vehicle) {
        if (origin == null || destination == null) throw new IllegalArgumentException("출발지와 도착지가 필요합니다.");
        if (vehicle == null) throw new IllegalArgumentException("차량 제원이 필요합니다.");
        if (!(startSoc >= 0 && startSoc <= 100)) throw new IllegalArgumentException("출발 SOC 는 0~100 이어야 합니다: " + startSoc);
        List<? extends ChargingStation> all = stations == null ? Collections.<ChargingStation>emptyList() : stations;

        if (!endpointsReachable(origin, destination, startSoc, all, vehicle)) {
            return EvRoutePlan.infeasible(origin, destination, 0);
        }

        List<ChargingStation> corridor = corridorCandidates(origin, destination, all);
        List<ChargingStation> fast = new ArrayList<ChargingStation>();
        for (ChargingStation s : corridor) {
            if (s.getMaxKw() >= minChargerKw) fast.add(s);
        }
        EvRoutePlan plan = search(origin, destination, startSoc, fast, vehicle);
        if (!plan.isFeasible() && fast.size() < corridor.size()) {
            plan = search(origin, destination, startSoc, corridor, vehicle);
        }
        if (!plan.isFeasible() && corridor.size() < all.size()) {
            plan = search(origin, destination, startSoc, new ArrayList<ChargingStation>(all), vehicle);
        }
        return plan;
    }

    /**
     * 경로가 존재하기 위한 필요조건: 직행할 수 있거나, (출발 잔량으로 닿는 충전소가 있고) 그리고
     * (목표 잔량으로 도착지에 닿는 충전소가 있다). 이를 만족해도 중간이 끊겨 불가능할 수는 있다.
     */
    boolean endpointsReachable(Location origin, Location destination, double startSoc,
                               List<? extends ChargingStation> stations, VehicleSpec vehicle) {
        double reserve = vehicle.getReserveSoc();
        if (startSoc - vehicle.socUsed(distance.km(origin, destination)) >= reserve) return true;
        boolean first = false;
        boolean last = false;
        for (ChargingStation s : stations) {
            if (!first) {
                double arrive = startSoc - vehicle.socUsed(distance.km(origin, s));
                first = arrive >= reserve && arrive < vehicle.getTargetSoc();
            }
            if (!last) last = vehicle.getTargetSoc() - vehicle.socUsed(distance.km(s, destination)) >= reserve;
            if (first && last) return true;
        }
        return false;
    }

    /** 출발지-도착지 회랑 안의 충전소 (지도에 후보 충전소를 표시할 때도 쓸 수 있다) */
    public List<ChargingStation> corridorCandidates(Location origin, Location destination, List<? extends ChargingStation> stations) {
        double direct = distance.km(origin, destination);
        double budget = Math.max(direct * detourRatio, minDetourKm);
        List<ChargingStation> result = new ArrayList<ChargingStation>();
        for (ChargingStation s : stations) {
            if (distance.km(origin, s) + distance.km(s, destination) <= direct + budget) result.add(s);
        }
        return result;
    }

    EvRoutePlan search(Location origin, Location destination, double startSoc,
                       List<ChargingStation> candidates, VehicleSpec vehicle) {
        int k = candidates.size();
        int size = k + 2;
        int dest = k + 1;
        Location[] nodes = new Location[size];
        nodes[0] = origin;
        for (int i = 0; i < k; i++) nodes[i + 1] = candidates.get(i);
        nodes[dest] = destination;

        double[] best = new double[size];
        double[] arriveSoc = new double[size];
        int[] prev = new int[size];
        boolean[] done = new boolean[size];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);
        best[0] = 0;
        arriveSoc[0] = startSoc;

        while (true) {
            int u = -1;
            for (int i = 0; i < size; i++) {
                if (!done[i] && best[i] != Double.POSITIVE_INFINITY && (u == -1 || best[i] < best[u])) u = i;
            }
            if (u == -1 || u == dest) break;
            done[u] = true;

            double departSoc = u == 0 ? startSoc : vehicle.getTargetSoc();
            for (int v = 1; v < size; v++) {
                if (done[v]) continue;
                double km = distance.km(nodes[u], nodes[v]);
                double arrive = departSoc - vehicle.socUsed(km);
                if (arrive < vehicle.getReserveSoc()) continue;
                double cost = vehicle.driveMinutes(km);
                if (v != dest) {
                    if (arrive >= vehicle.getTargetSoc()) continue;
                    cost += vehicle.chargeMinutes(arrive, vehicle.getTargetSoc(), ((ChargingStation) nodes[v]).getMaxKw())
                            + vehicle.getStopOverheadMin();
                }
                if (best[u] + cost < best[v]) {
                    best[v] = best[u] + cost;
                    prev[v] = u;
                    arriveSoc[v] = arrive;
                }
            }
        }

        if (best[dest] == Double.POSITIVE_INFINITY) return EvRoutePlan.infeasible(origin, destination, k);

        List<Integer> path = new ArrayList<Integer>();
        for (int v = dest; v != -1; v = prev[v]) path.add(v);
        Collections.reverse(path);

        List<EvRoutePlan.ChargeStop> stops = new ArrayList<EvRoutePlan.ChargeStop>();
        double totalKm = 0;
        double chargeMin = 0;
        for (int i = 1; i < path.size(); i++) {
            int from = path.get(i - 1);
            int to = path.get(i);
            totalKm += distance.km(nodes[from], nodes[to]);
            if (to != dest) {
                ChargingStation station = (ChargingStation) nodes[to];
                double minutes = vehicle.chargeMinutes(arriveSoc[to], vehicle.getTargetSoc(), station.getMaxKw())
                        + vehicle.getStopOverheadMin();
                stops.add(new EvRoutePlan.ChargeStop(station, arriveSoc[to], vehicle.getTargetSoc(), minutes));
                chargeMin += minutes;
            }
        }
        return new EvRoutePlan(origin, destination, true, stops, totalKm, vehicle.driveMinutes(totalKm),
                chargeMin, arriveSoc[dest], k);
    }
}
