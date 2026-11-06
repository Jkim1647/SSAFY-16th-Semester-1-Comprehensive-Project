package com.ssafy.enjoytrip.model.dto;

/** 시군구·콘텐츠 유형처럼 (코드, 이름) 한 쌍인 선택지. */
public class CodeName {

    private final int code;
    private final String name;

    public CodeName(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
