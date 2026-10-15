package com.ssafy.enjoytrip.algorithm.demo;

import com.ssafy.enjoytrip.algorithm.geo.Location;

import java.util.ArrayList;
import java.util.List;

/** 데모용 관광지 (주소·분류 코드 포함). BackEnd 에서는 AttractionDto 가 이 역할을 한다. */
public class Attraction extends Location {

    private final String addr;
    private final String lcls1;
    private final String lcls2;
    private final String lcls3;

    public Attraction(String id, String name, double lat, double lng, String addr, String lcls1, String lcls2, String lcls3) {
        super(id, name, lat, lng);
        this.addr = addr;
        this.lcls1 = lcls1;
        this.lcls2 = lcls2;
        this.lcls3 = lcls3;
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

    /** 추천용 특징: 대·중·소분류 코드 */
    public List<String> categoryCodes() {
        List<String> codes = new ArrayList<String>(3);
        if (!lcls1.isEmpty()) codes.add(lcls1);
        if (!lcls2.isEmpty()) codes.add(lcls2);
        if (!lcls3.isEmpty()) codes.add(lcls3);
        return codes;
    }
}
