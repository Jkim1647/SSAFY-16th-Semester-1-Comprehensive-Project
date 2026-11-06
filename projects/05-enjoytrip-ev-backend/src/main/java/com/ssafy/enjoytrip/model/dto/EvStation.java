package com.ssafy.enjoytrip.model.dto;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;

/** 충전소 (환경부 EvCharger 정보를 충전소 단위로 묶은 것). chargerCount = 동시에 예약할 수 있는 자리 수. */
public class EvStation {

    private String statId;
    private String name;
    private double latitude;
    private double longitude;
    private int maxKw;
    private int chargerCount;
    /** 근처 검색 결과일 때 기준점까지 거리(km). */
    private Double distanceKm;

    public ChargingStation toChargingStation() {
        return new ChargingStation(statId, name, latitude, longitude, maxKw);
    }

    public String getStatId() {
        return statId;
    }

    public void setStatId(String statId) {
        this.statId = statId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getMaxKw() {
        return maxKw;
    }

    public void setMaxKw(int maxKw) {
        this.maxKw = maxKw;
    }

    public int getChargerCount() {
        return chargerCount;
    }

    public void setChargerCount(int chargerCount) {
        this.chargerCount = chargerCount;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }
}
