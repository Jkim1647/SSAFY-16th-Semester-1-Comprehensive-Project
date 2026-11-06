package com.ssafy.enjoytrip.model.dao;

import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.model.dto.News;
import com.ssafy.enjoytrip.model.dto.SearchCondition;

public interface NewsDao {

    /** 이미 있는 link 는 건너뛰고(INSERT IGNORE) 새로 저장된 행 수를 돌려준다. */
    int insertIgnore(List<News> list);

    int count(SearchCondition condition);

    List<News> search(SearchCondition condition);

    /** 수집 검색어별 기사 수 (최근 수집 순) */
    Map<String, Integer> countByKeyword();
}
