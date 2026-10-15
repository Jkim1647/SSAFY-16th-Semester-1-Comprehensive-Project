package com.ssafy.enjoytrip.model.dto;

import java.util.List;

/** 관광지 API 응답 형식 (JSON). 화면은 spot 안의 공통 필드와 바깥의 기능별 필드를 함께 쓴다. */
public final class AttractionResponses {

    private AttractionResponses() {
    }

    /** 관광지 공통 정보 */
    public record Spot(String id, String name, String addr, double lat, double lng,
                       String category, String subCategory, int fastChargersNearby) {
    }

    /** #4 자동완성. correctedQuery 는 한/영 전환 오타를 바로잡아 찾았을 때만 값이 있다 */
    public record Autocomplete(String query, String correctedQuery, double elapsedUs, List<Spot> items) {
    }

    /**
     * #5·#9 검색 결과 1건. field 는 "name" 또는 "addr", positions 는 하이라이트 시작 위치(길이 keywordLength),
     * distance 는 자모 키 단위 편집 거리 (정확히 찾았으면 0)
     */
    public record SearchHit(Spot spot, String field, int keywordLength, List<Integer> positions, int distance) {
    }

    /**
     * @param mode           exact(KMP 정확 일치) / keyboard(한/영 전환 오타를 고쳐 정확 일치) / fuzzy(오타 허용 검색)
     * @param correctedQuery 한/영 전환 오타를 고친 검색어 (해당 없으면 null)
     * @param suggestion     fuzzy 일 때 "혹시 ○○을 찾으셨나요?" 에 보여 줄 말 (가장 가까운 결과에서 맞은 부분)
     */
    public record Search(String query, String mode, String correctedQuery, String suggestion, double elapsedMs, List<SearchHit> items) {
    }

    /** #6 정렬된 목록 1건 (기준 위치에서의 직선거리 km) */
    public record SortedSpot(Spot spot, double km) {
    }

    public record SortedList(String by, int total, double elapsedMs, List<SortedSpot> items) {
    }

    /** #7 추천 1건. 세부 점수는 각 0~1, km 는 기준 위치가 없으면 null */
    public record Recommendation(Spot spot, double score, double taste, double distance, double charger,
                                 int fastChargers, Double km) {
    }

    public record Recommend(double elapsedMs, List<Recommendation> items) {
    }

    public record Category(String code, String name, int count, int level) {
    }

    public record Meta(int attractionCount, List<Category> categories, int passwordIterations, boolean demoPepper) {
    }
}
