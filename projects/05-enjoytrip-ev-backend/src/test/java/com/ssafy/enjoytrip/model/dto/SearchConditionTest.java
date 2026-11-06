package com.ssafy.enjoytrip.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SearchConditionTest {

    @Test
    void rejectsKeysOutsideWhitelist() {
        SearchCondition c = new SearchCondition(Set.of("name"), "password_hash", "x", 1, 10);
        assertNull(c.normalizedKey(), "허용 목록 밖의 컬럼명은 SQL 에 들어가면 안 된다");
        assertFalse(c.hasKeyword());
    }

    @Test
    void escapesLikeWildcards() {
        SearchCondition c = new SearchCondition(Set.of("name"), "name", "50%_off", 1, 10);
        assertEquals("%50\\%\\_off%", c.likeWord());
    }

    @Test
    void pageMathClampsInput() {
        SearchCondition c = new SearchCondition(Set.of(), null, null, -3, 1000);
        assertEquals(1, c.getCurrentPage());
        assertEquals(100, c.getItemsPerPage());
        assertEquals(0, c.getOffset());
        Page<String> p = new Page<>(new SearchCondition(Set.of(), null, null, 7, 10), 95, java.util.List.of());
        assertEquals(10, p.getTotalPages());
        assertEquals(6, p.getStartPage());
        assertEquals(10, p.getEndPage());
    }
}
