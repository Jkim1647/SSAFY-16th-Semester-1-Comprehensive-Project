package com.ssafy.enjoytrip.model.dao;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;

import java.util.List;

/** 전기차 충전소 조회 (충전기 단위 공공데이터를 충전소 statId 단위로 묶은 것) */
public interface ChargingStationDao {

    List<ChargingStation> findAll();
}
