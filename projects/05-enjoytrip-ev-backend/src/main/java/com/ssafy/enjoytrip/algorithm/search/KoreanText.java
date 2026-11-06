package com.ssafy.enjoytrip.algorithm.search;

import java.util.Locale;

/** 검색용 문자열 정규화와 한글 초성 변환 */
public final class KoreanText {

    private static final char[] CHOSEONG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'};
    private static final char HANGUL_BEGIN = '가';
    private static final char HANGUL_END = '힣';
    /** 한 초성에 속하는 완성형 글자 수 (중성 21 × 종성 28) */
    private static final int PER_CHOSEONG = 21 * 28;

    private KoreanText() {
    }

    /** 소문자로 바꾸고 공백·기호를 지운다. "한려해상국립공원 (오동도)" → "한려해상국립공원오동도" */
    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        String lower = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** 완성형 한글을 초성으로 바꾼다 (다른 글자는 그대로). "담양" → "ㄷㅇ" */
    public static String toChoseong(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= HANGUL_BEGIN && c <= HANGUL_END ? CHOSEONG[(c - HANGUL_BEGIN) / PER_CHOSEONG] : c);
        }
        return sb.toString();
    }

    /** 입력이 자음(ㄱ~ㅎ)으로만 이루어졌는지 — 초성 검색으로 처리할지 판단 */
    public static boolean isChoseongOnly(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 'ㄱ' || c > 'ㅎ') return false;
        }
        return true;
    }
}
