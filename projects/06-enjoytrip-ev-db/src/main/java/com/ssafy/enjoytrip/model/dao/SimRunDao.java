package com.ssafy.enjoytrip.model.dao;

import java.util.List;

import com.ssafy.enjoytrip.model.dto.Reservation;
import com.ssafy.enjoytrip.model.dto.SimRunSummary;

/** 시뮬레이션 실행 기록과 실행별 좌석 결과. */
public interface SimRunDao {

    long insert(String mode, String queueType, int vehicles, int stations);

    void finish(long runId, String status, String queueType, int confirmed, int rejected, int overbooked);

    List<SimRunSummary> recent(int limit);

    /** 실행에서 확정된 예약 전부 (좌석 배치도용 — stat_id, charger_no, vehicle_no 만 채운다). */
    List<Reservation> findSeats(long runId);

    /** 서버가 꺼지면서 RUNNING 으로 남은 실행을 FAILED 로 정리한다. */
    void failStaleRuns();
}
