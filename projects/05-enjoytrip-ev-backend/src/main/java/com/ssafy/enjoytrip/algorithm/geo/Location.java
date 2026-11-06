package com.ssafy.enjoytrip.algorithm.geo;

import java.util.Objects;

/**
 * 위경도를 가진 지점 (관광지, 출발지, 충전소의 공통 타입).
 * BackEnd 에서는 AttractionDto 등을 이 타입으로 변환해서 알고리즘에 넘긴다.
 */
public class Location {

    private final String id;
    private final String name;
    private final double lat;
    private final double lng;

    public Location(String id, String name, double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("잘못된 좌표: " + lat + ", " + lng);
        }
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location)) return false;
        Location other = (Location) o;
        return Objects.equals(id, other.id) && lat == other.lat && lng == other.lng;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, lat, lng);
    }

    @Override
    public String toString() {
        return name + "(" + lat + ", " + lng + ")";
    }
}
