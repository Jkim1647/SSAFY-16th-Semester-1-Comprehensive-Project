package com.ssafy.enjoytrip.algorithm.trip;

import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlan;
import com.ssafy.enjoytrip.algorithm.route.RouteResult;

import java.util.Collections;
import java.util.List;

/** 방문 순서 + 구간별 충전 계획. 중간 구간이 불가능하면 그 구간까지만 legs 에 담긴다. */
public class EvTripPlan {

    private final RouteResult route;
    private final List<EvRoutePlan> legs;

    public EvTripPlan(RouteResult route, List<EvRoutePlan> legs) {
        this.route = route;
        this.legs = Collections.unmodifiableList(legs);
    }

    public RouteResult getRoute() {
        return route;
    }

    public List<EvRoutePlan> getLegs() {
        return legs;
    }

    public boolean isFeasible() {
        for (EvRoutePlan leg : legs) {
            if (!leg.isFeasible()) return false;
        }
        return true;
    }

    public double getTotalKm() {
        double sum = 0;
        for (EvRoutePlan leg : legs) sum += leg.getTotalKm();
        return sum;
    }

    public double getTotalMin() {
        double sum = 0;
        for (EvRoutePlan leg : legs) sum += leg.getTotalMin();
        return sum;
    }

    public int getChargeStopCount() {
        int count = 0;
        for (EvRoutePlan leg : legs) count += leg.getStops().size();
        return count;
    }
}
