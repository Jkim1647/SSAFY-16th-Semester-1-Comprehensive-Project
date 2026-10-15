package com.ssafy.enjoytrip.algorithm.trip;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlan;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlanner;
import com.ssafy.enjoytrip.algorithm.ev.VehicleSpec;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.route.RouteOptimizer;
import com.ssafy.enjoytrip.algorithm.route.RouteResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 두 알고리즘을 묶은 여행 계획 진입점.
 * 1) {@link RouteOptimizer} 로 관광지 방문 순서를 정하고
 * 2) 구간마다 {@link EvRoutePlanner} 로 충전소 경유 계획을 세운다. 앞 구간 도착 SOC 가 다음 구간 출발 SOC 가 된다.
 *
 * BackEnd 에서는 이 클래스를 @Service 로 감싸거나 @Bean 으로 등록해서 Controller 에서 호출한다.
 */
public class EvTripPlanner {

    private final RouteOptimizer routeOptimizer;
    private final EvRoutePlanner evRoutePlanner;

    public EvTripPlanner(RouteOptimizer routeOptimizer, EvRoutePlanner evRoutePlanner) {
        this.routeOptimizer = routeOptimizer;
        this.evRoutePlanner = evRoutePlanner;
    }

    public EvTripPlan plan(Location start, List<? extends Location> attractions, boolean returnToStart,
                           double startSoc, List<? extends ChargingStation> stations, VehicleSpec vehicle) {
        RouteResult route = routeOptimizer.optimize(start, attractions, returnToStart);

        List<Location> points = new ArrayList<Location>();
        points.add(start);
        points.addAll(route.getOrderedStops());
        if (returnToStart && !route.getOrderedStops().isEmpty()) points.add(start);

        List<EvRoutePlan> legs = new ArrayList<EvRoutePlan>();
        double soc = startSoc;
        for (int i = 1; i < points.size(); i++) {
            EvRoutePlan leg = evRoutePlanner.plan(points.get(i - 1), points.get(i), soc, stations, vehicle);
            legs.add(leg);
            if (!leg.isFeasible()) break;
            soc = leg.getArrivalSoc();
        }
        return new EvTripPlan(route, legs);
    }
}
