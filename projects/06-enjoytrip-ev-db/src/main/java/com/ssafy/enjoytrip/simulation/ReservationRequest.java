package com.ssafy.enjoytrip.simulation;

/**
 * 큐에 실리는 예약 요청 이벤트 (가상 차량 한 대의 한 번 시도).
 * Kafka 로 보낼 때 JSON 으로 직렬화되므로 단순 값만 담는다.
 *
 * @param runId     시뮬레이션 실행 번호
 * @param vehicleNo 차량 번호 (0부터)
 * @param ticketNo  대기 번호 — 처음 줄을 선 순서 (1부터)
 * @param attempt   몇 번째 희망 (0 = 1지망)
 */
public record ReservationRequest(long runId, int vehicleNo, int ticketNo, int attempt, String statId) {

    public ReservationRequest next(String nextStatId) {
        return new ReservationRequest(runId, vehicleNo, ticketNo, attempt + 1, nextStatId);
    }
}
