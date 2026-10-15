package com.ssafy.enjoytrip.algorithm.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 검색창 자동완성용 Trie (접두어 트리).
 *
 * <ul>
 *   <li>각 노드에 "이 접두어로 시작하는 항목 중 점수 상위 K개"를 미리 저장해 둔다 (노드별 Top-K 캐시).
 *       조회는 접두어 길이 P 만큼 내려가서 저장된 목록을 꺼내기만 하면 되므로 O(P + K) — 전체 항목 수와 무관하다.</li>
 *   <li>삽입은 경로의 노드마다 Top-K 목록을 갱신하므로 O(L · K) (L = 키워드 길이).</li>
 *   <li>이름 전체뿐 아니라 띄어쓰기로 나뉜 각 단어도 키로 넣어 "오동도" 로도 "한려해상국립공원 (오동도)" 를 찾는다.</li>
 *   <li>한글 초성 Trie 를 따로 두어 "ㄷㅇ" 처럼 자음만 입력해도 "담양 ..." 을 찾는다.</li>
 * </ul>
 *
 * 쓰기(add)와 읽기(suggest)가 섞이면 동기화가 필요하다. BackEnd 에서는 시작할 때 한 번 만들고 읽기만 하거나,
 * 데이터가 바뀌면 새 인스턴스를 만들어 참조를 바꿔 끼우는 방식을 권장한다.
 *
 * @param <T> 검색 결과로 돌려줄 항목 타입 (관광지 DTO 등)
 */
public class AutocompleteTrie<T> {

    public static final int DEFAULT_TOP_K = 10;

    private final int topK;
    private final Node<T> root = new Node<T>();
    private final Node<T> choseongRoot = new Node<T>();
    private int size;

    public AutocompleteTrie() {
        this(DEFAULT_TOP_K);
    }

    public AutocompleteTrie(int topK) {
        if (topK < 1) throw new IllegalArgumentException("topK 는 1 이상이어야 합니다.");
        this.topK = topK;
    }

    /**
     * @param name  검색 대상 이름 (예: 관광지명)
     * @param item  결과로 돌려줄 항목
     * @param score 정렬 점수 (클수록 먼저). 인기도, 조회수, 충전 편의 점수 등
     */
    public void add(String name, T item, double score) {
        if (name == null || item == null) throw new IllegalArgumentException("name 과 item 은 null 일 수 없습니다.");
        if (Double.isNaN(score)) throw new IllegalArgumentException("score 가 NaN 입니다.");
        Entry<T> entry = new Entry<T>(item, score, name);
        for (String key : keys(name)) {
            insert(root, key, entry);
            insert(choseongRoot, KoreanText.toChoseong(key), entry);
        }
        size++;
    }

    /** 접두어로 시작하는 항목을 점수 높은 순으로 최대 limit 개 (limit 은 topK 이하) */
    public List<T> suggest(String prefix, int limit) {
        if (limit < 1) return Collections.emptyList();
        String key = KoreanText.normalize(prefix);
        if (key.isEmpty()) return Collections.emptyList();
        Node<T> node = KoreanText.isChoseongOnly(key) ? find(choseongRoot, key) : find(root, key);
        if (node == null) return Collections.emptyList();
        List<T> result = new ArrayList<T>(Math.min(limit, node.top.size()));
        for (int i = 0; i < node.top.size() && i < limit; i++) result.add(node.top.get(i).item);
        return result;
    }

    public int size() {
        return size;
    }

    public int getTopK() {
        return topK;
    }

    /** 메모리 사용량을 가늠하기 위한 전체 노드 수 (일반 + 초성 Trie) */
    public int nodeCount() {
        return count(root) + count(choseongRoot);
    }

    private static <T> int count(Node<T> node) {
        int n = 1;
        for (Node<T> child : node.children.values()) n += count(child);
        return n;
    }

    /** 이름 전체 + 띄어쓰기로 나뉜 두 번째 이후 단어들 (정규화, 중복 제거) */
    static List<String> keys(String name) {
        List<String> keys = new ArrayList<String>();
        String whole = KoreanText.normalize(name);
        if (!whole.isEmpty()) keys.add(whole);
        String[] words = name.split("[\\s()\\[\\]·,/]+");
        for (int i = 1; i < words.length; i++) {
            String w = KoreanText.normalize(words[i]);
            if (!w.isEmpty() && !keys.contains(w)) keys.add(w);
        }
        return keys;
    }

    private void insert(Node<T> start, String key, Entry<T> entry) {
        Node<T> node = start;
        for (int i = 0; i < key.length(); i++) {
            Node<T> next = node.children.get(key.charAt(i));
            if (next == null) {
                next = new Node<T>();
                node.children.put(key.charAt(i), next);
            }
            node = next;
            offer(node.top, entry);
        }
    }

    /** 점수 내림차순(같으면 이름 오름차순) 목록에 넣고 K 개만 유지. 같은 항목이 다른 키로 이미 들어 있으면 건너뛴다. */
    private void offer(List<Entry<T>> top, Entry<T> entry) {
        for (Entry<T> e : top) {
            if (e.item.equals(entry.item)) return;
        }
        int pos = top.size();
        while (pos > 0 && entry.before(top.get(pos - 1))) pos--;
        if (pos >= topK) return;
        top.add(pos, entry);
        if (top.size() > topK) top.remove(top.size() - 1);
    }

    private static <T> Node<T> find(Node<T> start, String key) {
        Node<T> node = start;
        for (int i = 0; i < key.length() && node != null; i++) node = node.children.get(key.charAt(i));
        return node;
    }

    private static final class Node<T> {
        final Map<Character, Node<T>> children = new HashMap<Character, Node<T>>();
        final List<Entry<T>> top = new ArrayList<Entry<T>>(2);
    }

    private static final class Entry<T> {
        final T item;
        final double score;
        final String name;

        Entry(T item, double score, String name) {
            this.item = item;
            this.score = score;
            this.name = name;
        }

        boolean before(Entry<T> other) {
            if (score != other.score) return score > other.score;
            return name.compareTo(other.name) < 0;
        }
    }
}
