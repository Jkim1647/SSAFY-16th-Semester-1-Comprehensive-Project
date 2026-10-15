package com.ssafy.enjoytrip.model.dto;

import java.util.List;

/** 여행 계획 API (#1 방문 순서 + #2 충전 경로) 요청·응답 */
public final class TripDtos {

    private TripDtos() {
    }

    public record Point(String name, Double lat, Double lng) {
    }

    /**
     * @param start         출발지 (이름은 선택)
     * @param attractionIds 방문할 관광지 contentId (1~30개)
     * @param returnToStart 출발지로 돌아오는지 (기본 true)
     * @param startSoc      출발 배터리 % (기본 60)
     */
    public record PlanRequest(Point start, List<String> attractionIds, Boolean returnToStart, Double startSoc) {
    }

    public record Stop(String id, String name, double lat, double lng) {
    }

    public record Charge(String name, double lat, double lng, double kw, double arriveSoc, double departSoc, double min) {
    }

    public record Leg(String from, String to, boolean feasible, Double km, Double driveMin, Double chargeMin,
                      Double arrivalSoc, int candidates, List<Charge> charges) {
    }

    /**
     * @param method     방문 순서를 정한 방법 (HELD_KARP / NEAREST_NEIGHBOR_2OPT / TRIVIAL)
     * @param routeKm    방문 순서 기준 거리 (충전소 경유 전)
     * @param totalKm    충전소 경유를 포함한 실제 이동 거리
     * @param totalMin   주행 + 충전 시간
     */
    public record PlanResponse(boolean feasible, String method, Stop start, List<Stop> order, double routeKm,
                               double totalKm, double totalMin, int charges, double elapsedMs, List<Leg> legs) {
    }
}
