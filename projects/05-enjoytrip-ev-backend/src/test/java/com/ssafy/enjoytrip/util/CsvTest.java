package com.ssafy.enjoytrip.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void splitsQuotedFieldsWithCommasAndEscapedQuotes() {
        assertEquals(List.of("1", "a, b", "say \"hi\"", ""), Csv.split("1,\"a, b\",\"say \"\"hi\"\"\","));
    }

    @Test
    void readsBundledAttractionData() {
        List<Map<String, String>> rows = Csv.readResource("data/attractions.csv");
        assertEquals(true, rows.size() > 3000, "관광지 데이터가 3,000곳 이상 들어 있어야 한다");
        assertEquals(true, rows.get(0).containsKey("contentTypeId"));
    }
}
