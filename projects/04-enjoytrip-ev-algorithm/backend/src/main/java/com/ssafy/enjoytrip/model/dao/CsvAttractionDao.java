package com.ssafy.enjoytrip.model.dao;

import com.ssafy.enjoytrip.model.dto.AttractionDto;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** data/attractions-gwangju-jeonnam.csv (contentId,title,lat,lng,addr,lDongSignguCd,lcls1,lcls2,lcls3) — 시작할 때 한 번 읽어 메모리에 둔다 */
@Repository
public class CsvAttractionDao implements AttractionDao {

    static final String FILE = "attractions-gwangju-jeonnam.csv";

    private final List<AttractionDto> attractions;
    private final Map<String, AttractionDto> byId = new HashMap<>();

    public CsvAttractionDao(CsvDataSource source) {
        List<AttractionDto> list = new ArrayList<>();
        for (String[] f : source.rows(FILE)) {
            AttractionDto a = new AttractionDto(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]), f[4],
                    f.length > 6 ? f[6] : "", f.length > 7 ? f[7] : "", f.length > 8 ? f[8] : "");
            list.add(a);
            byId.put(a.getId(), a);
        }
        this.attractions = Collections.unmodifiableList(list);
    }

    @Override
    public List<AttractionDto> findAll() {
        return attractions;
    }

    @Override
    public Optional<AttractionDto> findById(String contentId) {
        return Optional.ofNullable(byId.get(contentId));
    }
}
