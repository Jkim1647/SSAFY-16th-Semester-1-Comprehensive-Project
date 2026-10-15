package com.ssafy.enjoytrip.algorithm.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 여러 필드(이름, 주소, 설명 등)에서 키워드를 KMP 로 찾고, 하이라이트할 위치까지 돌려준다.
 * 대소문자는 구분하지 않는다. 순위: 앞쪽 필드(이름)에서 찾은 것 → 등장 횟수 많은 것 → 더 앞에서 등장한 것.
 *
 * 시간 O(Σ 필드 길이 + 키워드 길이) — 키워드의 실패 함수는 한 번만 만든다.
 *
 * @param <T> 검색 대상 항목 타입
 */
public class KeywordSearcher<T> {

    /** 항목에서 검색할 필드들을 꺼내는 함수 (우선순위 순서) */
    public interface Fields<T> {
        String[] of(T item);
    }

    public static class Match<T> {
        private final T item;
        private final int fieldIndex;
        private final List<Integer> positions;
        private final int keywordLength;

        Match(T item, int fieldIndex, List<Integer> positions, int keywordLength) {
            this.item = item;
            this.fieldIndex = fieldIndex;
            this.positions = Collections.unmodifiableList(positions);
            this.keywordLength = keywordLength;
        }

        public T getItem() {
            return item;
        }

        /** 처음 찾은 필드 번호 (0 = 가장 우선) */
        public int getFieldIndex() {
            return fieldIndex;
        }

        /** 그 필드에서 키워드가 시작하는 위치들 — 화면에서 [pos, pos + keywordLength) 를 강조하면 된다 */
        public List<Integer> getPositions() {
            return positions;
        }

        public int getKeywordLength() {
            return keywordLength;
        }
    }

    private final Fields<T> fields;

    public KeywordSearcher(Fields<T> fields) {
        if (fields == null) throw new IllegalArgumentException("fields 가 null 입니다.");
        this.fields = fields;
    }

    public List<Match<T>> search(List<? extends T> items, String keyword, int limit) {
        String key = keyword == null ? "" : lower(keyword.trim());
        if (key.isEmpty() || limit < 1) return Collections.emptyList();
        KmpMatcher kmp = new KmpMatcher(key);

        List<Match<T>> matches = new ArrayList<Match<T>>();
        for (T item : items) {
            String[] values = fields.of(item);
            for (int f = 0; f < values.length; f++) {
                List<Integer> pos = kmp.findAll(lower(values[f]));
                if (!pos.isEmpty()) {
                    matches.add(new Match<T>(item, f, pos, key.length()));
                    break;
                }
            }
        }
        Collections.sort(matches, new Comparator<Match<T>>() {
            @Override
            public int compare(Match<T> a, Match<T> b) {
                if (a.fieldIndex != b.fieldIndex) return Integer.compare(a.fieldIndex, b.fieldIndex);
                if (a.positions.size() != b.positions.size()) return Integer.compare(b.positions.size(), a.positions.size());
                return Integer.compare(a.positions.get(0), b.positions.get(0));
            }
        });
        return matches.size() > limit ? new ArrayList<Match<T>>(matches.subList(0, limit)) : matches;
    }

    /** 글자 수가 바뀌지 않는 소문자 변환 (하이라이트 위치가 원문과 일치해야 하므로 String.toLowerCase 대신 사용) */
    static String lower(String s) {
        if (s == null) return "";
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) c[i] = Character.toLowerCase(c[i]);
        return new String(c);
    }
}
