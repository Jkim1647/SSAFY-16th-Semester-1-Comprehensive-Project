package com.ssafy.enjoytrip.model.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import com.ssafy.enjoytrip.model.dto.EvStation;
import com.ssafy.enjoytrip.model.dto.Reservation;

/**
 * 충전소·예약 데이터 접근.
 * Connection 을 받는 메서드는 서비스가 연 트랜잭션 안에서 호출된다 (DAO 가 commit 하지 않는다).
 */
public interface EvDao {

    EvStation findStation(String statId);

    /** 기준점에서 radiusKm 안의 충전소를 가까운 순으로. minKw 이상만. */
    List<EvStation> findNearby(double lat, double lng, double radiusKm, int minKw, int limit);

    List<EvStation> searchByName(String keyword, int limit);

    /** 전체 충전소 (경로 계획 후보). */
    List<EvStation> findAll(int minKw);

    // ---- 트랜잭션 안에서 쓰는 예약 연산 ----

    /** 슬롯 행이 없으면 만든다 (INSERT IGNORE). */
    void ensureSlot(Connection con, long runId, String statId, LocalDateTime slot, int capacity) throws SQLException;

    /** 슬롯 행을 잠그고 [capacity, booked] 를 돌려준다. 같은 슬롯을 노리는 다른 트랜잭션은 commit 까지 기다린다. */
    int[] lockSlot(Connection con, long runId, String statId, LocalDateTime slot) throws SQLException;

    void increaseBooked(Connection con, long runId, String statId, LocalDateTime slot, int delta) throws SQLException;

    /** 잠금 없이 확정 예약 수를 센다 (NAIVE 비교용). */
    int countConfirmed(Connection con, long runId, String statId, LocalDateTime slot) throws SQLException;

    long insertReservation(Connection con, Reservation r) throws SQLException;

    Reservation findReservationForUpdate(Connection con, long reservationId) throws SQLException;

    void updateStatus(Connection con, long reservationId, String status) throws SQLException;

    boolean hasMemberReservation(Connection con, long memberId, LocalDateTime slot) throws SQLException;

    /** 충전소가 달라도 같은 회원의 예약·취소는 한 트랜잭션씩 처리한다. */
    void lockMember(Connection con, long memberId) throws SQLException;

    /** 슬롯 잠금을 가진 상태에서 사용 가능한 가장 작은 충전기 번호를 찾는다. */
    int findAvailableCharger(Connection con, long runId, String statId, LocalDateTime slot, int capacity)
            throws SQLException;

    // ---- 조회 ----

    /** 해당 충전소의 [from, to) 확정 예약. */
    List<Reservation> findConfirmed(long runId, String statId, LocalDateTime from, LocalDateTime to);

    List<Reservation> findByMember(long memberId);

    /** 시뮬레이션 실행에서 정원을 넘긴 예약 수 = Σ max(0, 슬롯별 예약 수 − 충전기 수). */
    int countOverbooked(long runId);

    /** 오래된 시뮬레이션 데이터를 지운다 (최근 keep 개 실행만 남김). */
    void purgeOldRuns(int keep);
}
