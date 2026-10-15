package com.ssafy.enjoytrip.model.dto;

import com.ssafy.enjoytrip.algorithm.geo.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * 관광지. algorithm-core 의 Location 을 상속해 알고리즘(추천, 경로, 격자 인덱스)에 그대로 넘긴다.
 * lcls1~3 은 한국관광공사 신분류체계 대·중·소분류 코드.
 */
public class AttractionDto extends Location {

    private final String addr;
    private final String lcls1;
    private final String lcls2;
    private final String lcls3;

    public AttractionDto(String id, String name, double lat, double lng, String addr, String lcls1, String lcls2, String lcls3) {
        super(id, name, lat, lng);
        this.addr = addr == null ? "" : addr;
        this.lcls1 = lcls1 == null ? "" : lcls1;
        this.lcls2 = lcls2 == null ? "" : lcls2;
        this.lcls3 = lcls3 == null ? "" : lcls3;
    }

    public String getAddr() {
        return addr;
    }

    public String getLcls1() {
        return lcls1;
    }

    public String getLcls2() {
        return lcls2;
    }

    public String getLcls3() {
        return lcls3;
    }

    /** 추천(#7) 특징 벡터에 쓰는 분류 코드 */
    public List<String> categoryCodes() {
        List<String> codes = new ArrayList<>(3);
        if (!lcls1.isEmpty()) codes.add(lcls1);
        if (!lcls2.isEmpty()) codes.add(lcls2);
        if (!lcls3.isEmpty()) codes.add(lcls3);
        return codes;
    }
}
