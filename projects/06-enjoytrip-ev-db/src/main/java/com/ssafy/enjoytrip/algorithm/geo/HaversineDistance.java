package com.ssafy.enjoytrip.algorithm.geo;

/**
 * 하버사인 공식으로 구한 대원 거리에 우회 계수를 곱해 도로 거리를 근사한다.
 * 우회 계수 1.3 은 국내 도로 거리 / 직선 거리 비가 보통 1.2~1.4 라는 점을 반영한 기본값이다.
 */
public class HaversineDistance implements DistanceCalculator {

    public static final double EARTH_RADIUS_KM = 6371.0088;
    public static final double DEFAULT_DETOUR_FACTOR = 1.3;

    private final double detourFactor;

    public HaversineDistance() {
        this(DEFAULT_DETOUR_FACTOR);
    }

    public HaversineDistance(double detourFactor) {
        if (detourFactor < 1.0) {
            throw new IllegalArgumentException("우회 계수는 1 이상이어야 합니다: " + detourFactor);
        }
        this.detourFactor = detourFactor;
    }

    @Override
    public double km(Location a, Location b) {
        return greatCircleKm(a.getLat(), a.getLng(), b.getLat(), b.getLng()) * detourFactor;
    }

    public static double greatCircleKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
