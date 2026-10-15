package com.ssafy.enjoytrip.algorithm.geo;

/** 테스트에서 KakaoRoadDistance 의 응답 파싱(패키지 전용)을 호출하기 위한 통로 */
public final class KakaoRoadDistanceAccess {

    private KakaoRoadDistanceAccess() {
    }

    public static double parse(String body) {
        return KakaoRoadDistance.parseDistanceKm(body);
    }
}
