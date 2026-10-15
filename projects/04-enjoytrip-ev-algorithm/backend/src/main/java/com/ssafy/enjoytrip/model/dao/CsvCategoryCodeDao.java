package com.ssafy.enjoytrip.model.dao;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** data/lcls-codes.csv (code,name) */
@Repository
public class CsvCategoryCodeDao implements CategoryCodeDao {

    static final String FILE = "lcls-codes.csv";

    private final Map<String, String> names;

    public CsvCategoryCodeDao(CsvDataSource source) {
        Map<String, String> map = new HashMap<>();
        for (String[] f : source.rows(FILE)) map.put(f[0], f[1]);
        this.names = Collections.unmodifiableMap(map);
    }

    @Override
    public Map<String, String> findAll() {
        return names;
    }
}
