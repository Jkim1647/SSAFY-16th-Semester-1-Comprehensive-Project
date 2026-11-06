package com.ssafy.enjoytrip.algorithm.ev;

import com.ssafy.enjoytrip.algorithm.geo.Location;

/** 충전소 (한국환경공단 충전기 정보를 statId 단위로 묶은 것). maxKw 는 그 충전소에서 가장 빠른 충전기 출력. */
public class ChargingStation extends Location {

    private final double maxKw;

    public ChargingStation(String id, String name, double lat, double lng, double maxKw) {
        super(id, name, lat, lng);
        if (!(maxKw > 0)) throw new IllegalArgumentException("충전 출력은 0보다 커야 합니다: " + id + " " + maxKw);
        this.maxKw = maxKw;
    }

    public double getMaxKw() {
        return maxKw;
    }
}
