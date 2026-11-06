package com.ssafy.enjoytrip.model.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.ssafy.enjoytrip.model.dto.Plan;
import com.ssafy.enjoytrip.model.dto.PlanStop;

/**
 * 여행 계획 DAO. 저장(계획 + 방문지 전체 교체)은 하나의 트랜잭션이어야 하므로
 * Connection 을 받는 메서드는 서비스가 연 Connection 을 그대로 쓴다.
 */
public interface PlanDao {

    /** @return 새로 생성된 plan_id */
    long insert(Connection con, Plan plan) throws SQLException;

    /** 본인 계획만 수정된다 (WHERE plan_id = ? AND member_id = ?). @return 수정된 행 수 */
    int update(Connection con, Plan plan) throws SQLException;

    /** 기존 방문지를 지우고 stops 순서대로 seq 1, 2, … 로 다시 넣는다. */
    void replaceStops(Connection con, long planId, List<PlanStop> stops) throws SQLException;

    /** 회원의 계획 목록 (방문지 수·경비 합계 집계 포함, 최근 수정순). */
    List<Plan> findByMember(long memberId);

    /** 방문지(관광지 정보 JOIN)까지 채운 계획. 없으면 null. */
    Plan findById(long planId);

    /** 본인 계획만 삭제된다. 방문지는 FK ON DELETE CASCADE 로 함께 지워진다. @return 삭제된 행 수 */
    int delete(long planId, long memberId);
}
