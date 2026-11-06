package com.ssafy.enjoytrip.algorithm.route;

import com.ssafy.enjoytrip.algorithm.geo.Location;

import java.util.Collections;
import java.util.List;

/** 방문 순서 최적화 결과. orderedStops 에 출발지는 포함하지 않는다. */
public class RouteResult {

    public enum Method {
        /** 경유지가 없거나 1곳이라 계산할 필요가 없음 */
        TRIVIAL,
        /** 비트마스크 DP (Held-Karp) — 최적해 보장 */
        HELD_KARP,
        /** 최근접 이웃 + 2-opt — 근사해 */
        NEAREST_NEIGHBOR_2OPT
    }

    private final Location start;
    private final List<Location> orderedStops;
    private final double totalKm;
    private final boolean returnToStart;
    private final Method method;

    public RouteResult(Location start, List<Location> orderedStops, double totalKm, boolean returnToStart, Method method) {
        this.start = start;
        this.orderedStops = Collections.unmodifiableList(orderedStops);
        this.totalKm = totalKm;
        this.returnToStart = returnToStart;
        this.method = method;
    }

    public Location getStart() {
        return start;
    }

    public List<Location> getOrderedStops() {
        return orderedStops;
    }

    public double getTotalKm() {
        return totalKm;
    }

    public boolean isReturnToStart() {
        return returnToStart;
    }

    public Method getMethod() {
        return method;
    }
}
