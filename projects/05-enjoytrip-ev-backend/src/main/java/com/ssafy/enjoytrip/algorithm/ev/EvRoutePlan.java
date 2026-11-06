package com.ssafy.enjoytrip.algorithm.ev;

import com.ssafy.enjoytrip.algorithm.geo.Location;

import java.util.Collections;
import java.util.List;

/** 출발지 → (충전소 0곳 이상) → 도착지 경로 계획 결과 */
public class EvRoutePlan {

    /** 경로 중 충전 1회 */
    public static class ChargeStop {
        private final ChargingStation station;
        private final double arriveSoc;
        private final double departSoc;
        private final double chargeMin;

        public ChargeStop(ChargingStation station, double arriveSoc, double departSoc, double chargeMin) {
            this.station = station;
            this.arriveSoc = arriveSoc;
            this.departSoc = departSoc;
            this.chargeMin = chargeMin;
        }

        public ChargingStation getStation() {
            return station;
        }

        public double getArriveSoc() {
            return arriveSoc;
        }

        public double getDepartSoc() {
            return departSoc;
        }

        /** 충전 시간 + 충전 1회 고정 소요 시간 */
        public double getChargeMin() {
            return chargeMin;
        }
    }

    private final Location origin;
    private final Location destination;
    private final boolean feasible;
    private final List<ChargeStop> stops;
    private final double totalKm;
    private final double driveMin;
    private final double chargeMin;
    private final double arrivalSoc;
    private final int candidateCount;

    public EvRoutePlan(Location origin, Location destination, boolean feasible, List<ChargeStop> stops, double totalKm,
                       double driveMin, double chargeMin, double arrivalSoc, int candidateCount) {
        this.origin = origin;
        this.destination = destination;
        this.feasible = feasible;
        this.stops = Collections.unmodifiableList(stops);
        this.totalKm = totalKm;
        this.driveMin = driveMin;
        this.chargeMin = chargeMin;
        this.arrivalSoc = arrivalSoc;
        this.candidateCount = candidateCount;
    }

    static EvRoutePlan infeasible(Location origin, Location destination, int candidateCount) {
        return new EvRoutePlan(origin, destination, false, Collections.<ChargeStop>emptyList(),
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, candidateCount);
    }

    public Location getOrigin() {
        return origin;
    }

    public Location getDestination() {
        return destination;
    }

    /** false 면 주어진 충전소로는 잔량을 지키며 도착할 수 없다 (나머지 수치는 NaN) */
    public boolean isFeasible() {
        return feasible;
    }

    public List<ChargeStop> getStops() {
        return stops;
    }

    public double getTotalKm() {
        return totalKm;
    }

    public double getDriveMin() {
        return driveMin;
    }

    public double getChargeMin() {
        return chargeMin;
    }

    public double getTotalMin() {
        return driveMin + chargeMin;
    }

    /** 도착지에 도착했을 때 남은 SOC (%) */
    public double getArrivalSoc() {
        return arrivalSoc;
    }

    /** 경로 탐색에 실제로 사용한 후보 충전소 수 (회랑 필터 통과 수) */
    public int getCandidateCount() {
        return candidateCount;
    }
}
