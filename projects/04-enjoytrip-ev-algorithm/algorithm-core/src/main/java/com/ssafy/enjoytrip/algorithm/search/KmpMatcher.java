package com.ssafy.enjoytrip.algorithm.search;

import java.util.ArrayList;
import java.util.List;

/**
 * KMP(Knuth-Morris-Pratt) 문자열 검색.
 *
 * <p>실패 함수 fail[i] = pattern[0..i] 의 "접두사이면서 접미사인 가장 긴 부분(자기 자신 제외)" 길이.
 * 비교하다 어긋나면 본문 위치는 되돌리지 않고 패턴 위치만 fail 값으로 옮기므로,
 * 본문 길이 N, 패턴 길이 M 일 때 O(N + M) 에 모든 등장 위치를 찾는다. (단순 비교는 최악 O(N·M))
 *
 * <p>하나의 패턴으로 여러 본문을 검색하는 경우가 많아서, 실패 함수를 한 번 만든 인스턴스를 재사용한다.
 */
public final class KmpMatcher {

    private final String pattern;
    private final int[] fail;

    public KmpMatcher(String pattern) {
        if (pattern == null || pattern.isEmpty()) throw new IllegalArgumentException("검색어가 비어 있습니다.");
        this.pattern = pattern;
        this.fail = failure(pattern);
    }

    public static int[] failure(String p) {
        int[] fail = new int[p.length()];
        int j = 0;
        for (int i = 1; i < p.length(); i++) {
            while (j > 0 && p.charAt(i) != p.charAt(j)) j = fail[j - 1];
            if (p.charAt(i) == p.charAt(j)) j++;
            fail[i] = j;
        }
        return fail;
    }

    /** 본문에서 패턴이 시작하는 모든 위치 (겹치는 등장 포함) */
    public List<Integer> findAll(String text) {
        List<Integer> positions = new ArrayList<Integer>();
        if (text == null) return positions;
        int j = 0;
        for (int i = 0; i < text.length(); i++) {
            while (j > 0 && text.charAt(i) != pattern.charAt(j)) j = fail[j - 1];
            if (text.charAt(i) == pattern.charAt(j)) {
                if (j == pattern.length() - 1) {
                    positions.add(i - j);
                    j = fail[j];
                } else {
                    j++;
                }
            }
        }
        return positions;
    }

    public boolean matches(String text) {
        if (text == null) return false;
        int j = 0;
        for (int i = 0; i < text.length(); i++) {
            while (j > 0 && text.charAt(i) != pattern.charAt(j)) j = fail[j - 1];
            if (text.charAt(i) == pattern.charAt(j)) {
                if (j == pattern.length() - 1) return true;
                j++;
            }
        }
        return false;
    }

    public String getPattern() {
        return pattern;
    }
}
