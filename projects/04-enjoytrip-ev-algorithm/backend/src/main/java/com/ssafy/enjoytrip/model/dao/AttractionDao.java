package com.ssafy.enjoytrip.model.dao;

import com.ssafy.enjoytrip.model.dto.AttractionDto;

import java.util.List;
import java.util.Optional;

/** 관광지 조회. 지금은 CSV 구현(CsvAttractionDao), BackEnd 관통에서 MyBatis/JPA 구현으로 바꾼다. */
public interface AttractionDao {

    List<AttractionDto> findAll();

    Optional<AttractionDto> findById(String contentId);
}
