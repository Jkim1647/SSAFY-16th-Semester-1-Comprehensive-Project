package com.ssafy.enjoytrip.controller;

import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.Autocomplete;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.Recommend;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.Search;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.SortedList;
import com.ssafy.enjoytrip.model.service.AttractionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 관광지 검색·정렬·추천 API */
@RestController
@RequestMapping("/api/attractions")
public class AttractionController {

    /** 기준 위치를 주지 않았을 때 쓰는 광주송정역 */
    static final double DEFAULT_LAT = 35.1378;
    static final double DEFAULT_LNG = 126.7932;

    private final AttractionService service;

    public AttractionController(AttractionService service) {
        this.service = service;
    }

    /** #4 자동완성 (초성 검색 포함) */
    @GetMapping("/autocomplete")
    public Autocomplete autocomplete(@RequestParam(defaultValue = "") String q,
                                     @RequestParam(defaultValue = "8") int limit) {
        return service.autocomplete(q, range("limit", limit, 1, 10));
    }

    /** #5 이름·주소 키워드 검색 (KMP) + 하이라이트 위치 */
    @GetMapping("/search")
    public Search search(@RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "30") int limit) {
        return service.search(q, range("limit", limit, 1, 100));
    }

    /** #6 목록 정렬 (TimSort). by = distance | chargers | name */
    @GetMapping
    public SortedList list(@RequestParam(defaultValue = "distance") String by,
                           @RequestParam(defaultValue = "") String category,
                           @RequestParam(defaultValue = "" + DEFAULT_LAT) double lat,
                           @RequestParam(defaultValue = "" + DEFAULT_LNG) double lng,
                           @RequestParam(defaultValue = "30") int limit) {
        return service.sorted(by, category, new Location("base", "기준 위치", lat, lng), range("limit", limit, 1, 200));
    }

    /**
     * #7 추천. liked = 찜한 관광지 id 목록(쉼표), prefer = 선호 분류 코드 목록(쉼표).
     * lat·lng 를 주지 않으면 거리 점수를 쓰지 않는다.
     */
    @GetMapping("/recommend")
    public Recommend recommend(@RequestParam(required = false) String liked,
                               @RequestParam(required = false) String prefer,
                               @RequestParam(required = false) Double lat,
                               @RequestParam(required = false) Double lng,
                               @RequestParam(defaultValue = "0.6") double wTaste,
                               @RequestParam(defaultValue = "0.25") double wDistance,
                               @RequestParam(defaultValue = "0.15") double wCharger,
                               @RequestParam(defaultValue = "10") int k) {
        if ((lat == null) != (lng == null)) throw new IllegalArgumentException("기준 위치는 lat 과 lng 를 함께 주세요.");
        Location base = lat == null ? null : new Location("base", "기준 위치", lat, lng);
        return service.recommend(csv(liked), csv(prefer), base, wTaste, wDistance, wCharger, range("k", k, 1, 50));
    }

    static int range(String name, int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException(name + " 는 " + min + "~" + max + " 사이여야 합니다.");
        return value;
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
