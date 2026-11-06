package com.ssafy.enjoytrip.algorithm.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 오타를 허용하는 검색. 검색어와 항목의 각 필드(이름, 주소 …)를 자모 키 단위로 풀어,
 * 필드의 어떤 부분이 검색어와 편집 거리 k 이하인 항목을 찾는다.
 *
 * <ol>
 *   <li><b>후보 줄이기 (q-gram 필터)</b>: 자모 3개씩 묶은 3-gram 역색인을 만들어 둔다. 편집 한 번은 검색어의 3-gram 을
 *       최대 4개(인접 전치) 깨뜨리므로, 거리 k 이하로 맞는 항목은 검색어의 서로 다른 3-gram 중 최소 (개수 - 4k)개를 갖는다.
 *       이 조건을 만족하지 않는 항목은 정답일 수 없어 비교하지 않는다 (정답을 놓치지 않는 필터).</li>
 *   <li><b>정밀 비교</b>: 남은 후보만 {@link EditDistance#bestSubstring} (O(M·N) DP)으로 거리를 재고 맞은 구간을 구한다.</li>
 * </ol>
 * 허용 거리 k 는 검색어 길이에 비례한다 (짧은 검색어에서 엉뚱한 결과가 나오지 않게). 만든 뒤에는 읽기만 하므로 스레드 안전하다.
 *
 * @param <T> 검색 대상 항목 타입
 */
public class FuzzySearcher<T> {

    private static final int Q = 3;
    /**
     * 검색어 자모 키 수 상한. 후보마다 (검색어 키 수 × 필드 키 수) 크기의 DP 표를 만들므로,
     * 아주 긴 검색어 하나로 메모리·CPU 를 크게 쓰지 못하게 막는다 (한글 30글자 × 최대 5키 = 150키를 넘지 않게).
     */
    public static final int MAX_QUERY_KEYS = 160;
    /** 인접 전치 1번이 깨뜨릴 수 있는 3-gram 최대 개수 */
    private static final int GRAMS_PER_EDIT = Q + 1;

    public static class Match<T> {
        private final T item;
        private final int id;
        private final int distance;
        private final int fieldIndex;
        private final int start;
        private final int end;

        Match(T item, int id, int distance, int fieldIndex, int start, int end) {
            this.item = item;
            this.id = id;
            this.distance = distance;
            this.fieldIndex = fieldIndex;
            this.start = start;
            this.end = end;
        }

        public T getItem() {
            return item;
        }

        /** 자모 키 단위 편집 거리 (0 이면 공백·대소문자만 다르고 같은 글자) */
        public int getDistance() {
            return distance;
        }

        public int getFieldIndex() {
            return fieldIndex;
        }

        /** 원문 필드에서 맞은 구간 [start, end) — 하이라이트용 */
        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }
    }

    /** 결과와 함께 몇 개를 실제로 비교했는지 (필터 효과 측정용) */
    public static class Result<T> {
        private final List<Match<T>> matches;
        private final int candidates;
        private final int maxDistance;

        Result(List<Match<T>> matches, int candidates, int maxDistance) {
            this.matches = Collections.unmodifiableList(matches);
            this.candidates = candidates;
            this.maxDistance = maxDistance;
        }

        public List<Match<T>> getMatches() {
            return matches;
        }

        public int getCandidates() {
            return candidates;
        }

        public int getMaxDistance() {
            return maxDistance;
        }
    }

    private final List<T> items = new ArrayList<>();
    private final List<Hangul.Keys[]> keys = new ArrayList<>();
    private final List<String[]> texts = new ArrayList<>();
    private final Map<Long, int[]> index = new HashMap<>();

    public FuzzySearcher(List<? extends T> items, KeywordSearcher.Fields<? super T> fields) {
        if (items == null || fields == null) throw new IllegalArgumentException("items 와 fields 가 필요합니다.");
        Map<Long, List<Integer>> postings = new HashMap<>();
        for (T item : items) {
            int id = this.items.size();
            String[] values = fields.of(item);
            Hangul.Keys[] k = new Hangul.Keys[values.length];
            Set<Long> grams = new HashSet<>();
            for (int f = 0; f < values.length; f++) {
                k[f] = Hangul.toKeys(values[f] == null ? "" : values[f]);
                grams.addAll(grams(k[f].keys));
            }
            for (Long g : grams) postings.computeIfAbsent(g, x -> new ArrayList<>()).add(id);
            this.items.add(item);
            this.keys.add(k);
            this.texts.add(values);
        }
        for (Map.Entry<Long, List<Integer>> e : postings.entrySet()) {
            int[] arr = new int[e.getValue().size()];
            for (int i = 0; i < arr.length; i++) arr[i] = e.getValue().get(i);
            index.put(e.getKey(), arr);
        }
    }

    /** 검색어 자모 길이에 따른 허용 편집 거리: 3키 이하 0, 7키 이하 1, 12키 이하 2, 그 이상 3 */
    public static int defaultMaxDistance(int keyLength) {
        if (keyLength <= 3) return 0;
        if (keyLength <= 7) return 1;
        if (keyLength <= 12) return 2;
        return 3;
    }

    public Result<T> search(String query, int limit) {
        Hangul.Keys q = Hangul.toKeys(query == null ? "" : query);
        return search(q, defaultMaxDistance(q.length()), limit, true);
    }

    /**
     * @param useIndex false 면 필터 없이 모든 항목을 비교한다 (테스트·성능 비교용)
     */
    public Result<T> search(Hangul.Keys q, int maxDistance, int limit, boolean useIndex) {
        if (q.length() > MAX_QUERY_KEYS) throw new IllegalArgumentException("검색어가 너무 깁니다.");
        if (q.length() == 0 || limit < 1) return new Result<>(Collections.<Match<T>>emptyList(), 0, maxDistance);
        int[] candidates = useIndex ? candidates(q.keys, maxDistance) : null;
        int count = candidates == null ? items.size() : candidates.length;

        List<Match<T>> matches = new ArrayList<>();
        for (int c = 0; c < count; c++) {
            int id = candidates == null ? c : candidates[c];
            Hangul.Keys[] fields = keys.get(id);
            Match<T> best = null;
            for (int f = 0; f < fields.length; f++) {
                EditDistance.Match m = EditDistance.bestSubstring(q.keys, fields[f].keys, maxDistance);
                if (m == null || (best != null && m.distance >= best.distance)) continue;
                int[] src = fields[f].source;
                int start = m.end > m.start ? src[m.start] : 0;
                int end = m.end > m.start ? src[m.end - 1] + 1 : 0;
                best = new Match<>(items.get(id), id, m.distance, f, start, end);
            }
            if (best != null) matches.add(best);
        }
        // 거리 → 앞쪽 필드(이름) → 원문이 짧은 것(더 딱 맞는 이름) 순
        matches.sort((a, b) -> {
            if (a.distance != b.distance) return Integer.compare(a.distance, b.distance);
            if (a.fieldIndex != b.fieldIndex) return Integer.compare(a.fieldIndex, b.fieldIndex);
            int len = Integer.compare(fieldLength(a), fieldLength(b));
            return len != 0 ? len : Integer.compare(a.id, b.id);
        });
        List<Match<T>> top = matches.size() > limit ? new ArrayList<>(matches.subList(0, limit)) : matches;
        return new Result<>(top, count, maxDistance);
    }

    /** q-gram 필터를 통과한 항목 번호. 필터를 쓸 수 없으면(검색어가 짧거나 허용 거리가 커서 하한이 0 이하) null = 전부 */
    int[] candidates(char[] q, int maxDistance) {
        Set<Long> queryGrams = grams(q);
        int required = queryGrams.size() - GRAMS_PER_EDIT * maxDistance;
        if (required <= 0) return null;
        int[] counts = new int[items.size()];
        for (Long g : queryGrams) {
            int[] post = index.get(g);
            if (post == null) continue;
            for (int id : post) counts[id]++;
        }
        int n = 0;
        for (int c : counts) if (c >= required) n++;
        int[] result = new int[n];
        int k = 0;
        for (int id = 0; id < counts.length; id++) if (counts[id] >= required) result[k++] = id;
        return result;
    }

    private static Set<Long> grams(char[] s) {
        Set<Long> set = new HashSet<>();
        for (int i = 0; i + Q <= s.length; i++) {
            set.add(((long) s[i] << 32) | ((long) s[i + 1] << 16) | s[i + 2]);
        }
        return set;
    }

    private int fieldLength(Match<T> m) {
        String v = texts.get(m.id)[m.fieldIndex];
        return v == null ? 0 : v.length();
    }
}
