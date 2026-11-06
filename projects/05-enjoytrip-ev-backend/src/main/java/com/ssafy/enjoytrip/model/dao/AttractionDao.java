package com.ssafy.enjoytrip.model.dao;

import java.util.List;

import com.ssafy.enjoytrip.model.dto.Attraction;
import com.ssafy.enjoytrip.model.dto.AttractionCondition;
import com.ssafy.enjoytrip.model.dto.CodeName;

public interface AttractionDao {

    int count(AttractionCondition condition);

    List<Attraction> search(AttractionCondition condition);

    Attraction findById(int contentId);

    /** 여행 계획처럼 여러 건을 한 번에 가져올 때. 순서는 보장하지 않는다. */
    List<Attraction> findByIds(List<Integer> contentIds);

    List<CodeName> findSigngu();

    List<CodeName> findContentTypes();
}
