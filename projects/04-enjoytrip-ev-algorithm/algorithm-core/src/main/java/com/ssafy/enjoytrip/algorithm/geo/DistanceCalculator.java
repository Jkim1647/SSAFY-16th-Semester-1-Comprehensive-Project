package com.ssafy.enjoytrip.algorithm.geo;

/**
 * 두 지점 사이 이동 거리(km).
 * 기본 구현은 {@link HaversineDistance} 이고, BackEnd 에서 카카오모빌리티 등 실제 도로 거리로 바꿔 끼울 수 있다.
 * 알고리즘은 대칭(a→b == b→a)이라고 가정한다.
 */
@FunctionalInterface
public interface DistanceCalculator {

    double km(Location a, Location b);
}
