package com.ssafy.enjoytrip.model.dao;

import java.util.Map;

/** 한국관광공사 신분류체계 코드 → 이름 */
public interface CategoryCodeDao {

    Map<String, String> findAll();
}
