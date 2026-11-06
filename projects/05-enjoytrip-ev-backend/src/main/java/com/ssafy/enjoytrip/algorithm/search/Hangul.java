package com.ssafy.enjoytrip.algorithm.search;

import java.util.HashMap;
import java.util.Map;

/**
 * 한글을 두벌식 자판의 "키 입력 단위" 자모로 풀고(분해), 키 입력 순서대로 다시 글자로 조합한다.
 *
 * <ul>
 *   <li>분해: "왕" → ㅇ ㅗ ㅏ ㅇ, "닭" → ㄷ ㅏ ㄹ ㄱ. 겹모음·겹받침도 실제로 누르는 키 2개로 나눈다.
 *       오타는 키를 잘못 누르는 것이므로, 이 단위로 편집 거리를 재면 "받침 하나 틀림"이 거리 1 이 된다.</li>
 *   <li>조합: 두벌식 오토마타. 받침 뒤에 모음이 오면 받침이 다음 글자 초성으로 넘어간다 (각 + ㅏ → 가가).</li>
 *   <li>한/영 전환 오타 복원: 영문 상태로 친 "ekadid" 를 자판 위치대로 자모로 바꾼 뒤 조합하면 "담양".</li>
 * </ul>
 */
public final class Hangul {

    static final char BASE = '가';
    static final char LAST = '힣';
    static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    /** 첫 칸은 받침 없음 */
    static final String JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

    /** 두 키로 이루어진 겹모음·겹받침: "ㅗㅏ" → ㅘ */
    private static final Map<String, Character> COMBINED_VOWEL = new HashMap<>();
    private static final Map<String, Character> COMBINED_FINAL = new HashMap<>();
    /** 겹모음·겹받침 → 키 2개 */
    private static final Map<Character, String> SPLIT = new HashMap<>();
    /** 영문 자판 → 자모 (Shift 는 쌍자음·ㅒ·ㅖ) */
    private static final Map<Character, Character> QWERTY = new HashMap<>();
    private static final Map<Character, Character> TO_QWERTY = new HashMap<>();

    static {
        String[][] vowels = {{"ㅗㅏ", "ㅘ"}, {"ㅗㅐ", "ㅙ"}, {"ㅗㅣ", "ㅚ"}, {"ㅜㅓ", "ㅝ"}, {"ㅜㅔ", "ㅞ"}, {"ㅜㅣ", "ㅟ"}, {"ㅡㅣ", "ㅢ"}};
        String[][] finals = {{"ㄱㅅ", "ㄳ"}, {"ㄴㅈ", "ㄵ"}, {"ㄴㅎ", "ㄶ"}, {"ㄹㄱ", "ㄺ"}, {"ㄹㅁ", "ㄻ"}, {"ㄹㅂ", "ㄼ"},
                {"ㄹㅅ", "ㄽ"}, {"ㄹㅌ", "ㄾ"}, {"ㄹㅍ", "ㄿ"}, {"ㄹㅎ", "ㅀ"}, {"ㅂㅅ", "ㅄ"}};
        for (String[] v : vowels) {
            COMBINED_VOWEL.put(v[0], v[1].charAt(0));
            SPLIT.put(v[1].charAt(0), v[0]);
        }
        for (String[] f : finals) {
            COMBINED_FINAL.put(f[0], f[1].charAt(0));
            SPLIT.put(f[1].charAt(0), f[0]);
        }
        String keys = "qwertyuiopasdfghjklzxcvbnm";
        String jamo = "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ";
        for (int i = 0; i < keys.length(); i++) {
            QWERTY.put(keys.charAt(i), jamo.charAt(i));
            QWERTY.put(Character.toUpperCase(keys.charAt(i)), jamo.charAt(i));
            TO_QWERTY.put(jamo.charAt(i), keys.charAt(i));
        }
        String shiftKeys = "QWERTOP";
        String shiftJamo = "ㅃㅉㄸㄲㅆㅒㅖ";
        for (int i = 0; i < shiftKeys.length(); i++) {
            QWERTY.put(shiftKeys.charAt(i), shiftJamo.charAt(i));
            TO_QWERTY.put(shiftJamo.charAt(i), shiftKeys.charAt(i));
        }
    }

    private Hangul() {
    }

    public static boolean isSyllable(char c) {
        return c >= BASE && c <= LAST;
    }

    static boolean isConsonant(char c) {
        return c >= 'ㄱ' && c <= 'ㅎ';
    }

    static boolean isVowel(char c) {
        return c >= 'ㅏ' && c <= 'ㅣ';
    }

    /** 분해 결과: 자모 배열과, 각 자모가 원문의 몇 번째 글자에서 왔는지 (하이라이트 위치 계산용) */
    public static final class Keys {
        public final char[] keys;
        public final int[] source;

        Keys(char[] keys, int[] source) {
            this.keys = keys;
            this.source = source;
        }

        public int length() {
            return keys.length;
        }
    }

    /**
     * 키 입력 단위로 분해한다. 영문은 소문자로 바꾸고, 공백·기호는 건너뛴다 (띄어쓰기 차이는 오타로 세지 않음).
     */
    public static Keys toKeys(String s) {
        StringBuilder out = new StringBuilder(s.length() * 3);
        // 한 글자는 최대 5키 (초성 1 + 겹모음 2 + 겹받침 2, 예: 뷁 = ㅂ ㅜ ㅔ ㄹ ㄱ)
        int[] src = new int[s.length() * 5 + 1];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String part;
            if (isSyllable(c)) {
                int code = c - BASE;
                part = String.valueOf(CHO.charAt(code / (21 * 28)))
                        + split(JUNG.charAt((code / 28) % 21))
                        + (code % 28 == 0 ? "" : split(JONG.charAt(code % 28)));
            } else if (isConsonant(c) || isVowel(c)) {
                part = split(c);
            } else if (Character.isLetterOrDigit(c)) {
                part = String.valueOf(Character.toLowerCase(c));
            } else {
                continue;
            }
            for (int k = 0; k < part.length(); k++) {
                out.append(part.charAt(k));
                src[n++] = i;
            }
        }
        char[] keys = out.toString().toCharArray();
        int[] source = new int[n];
        System.arraycopy(src, 0, source, 0, n);
        return new Keys(keys, source);
    }

    private static String split(char jamo) {
        String s = SPLIT.get(jamo);
        return s == null ? String.valueOf(jamo) : s;
    }

    /** 키 입력 순서의 자모를 두벌식 규칙으로 조합한다. 자모가 아닌 글자는 그대로 둔다. */
    public static String compose(CharSequence keys) {
        StringBuilder out = new StringBuilder();
        char cho = 0;
        char jung = 0;
        char jong = 0;
        for (int i = 0; i < keys.length(); i++) {
            char c = keys.charAt(i);
            if (isConsonant(c)) {
                if (jung == 0) {
                    // 초성만 있거나 비어 있음 → 앞 초성은 낱자로 내보내고 새 초성
                    flush(out, cho, 0, 0);
                    cho = c;
                } else if (cho == 0) {
                    // 모음만 있던 경우
                    flush(out, 0, jung, 0);
                    jung = 0;
                    cho = c;
                } else if (jong == 0) {
                    if (JONG.indexOf(c) > 0) {
                        jong = c;
                    } else { // ㄸ ㅃ ㅉ 는 받침이 될 수 없다
                        flush(out, cho, jung, 0);
                        cho = c;
                        jung = 0;
                    }
                } else {
                    Character combined = COMBINED_FINAL.get("" + jong + c);
                    if (combined != null) {
                        jong = combined;
                    } else {
                        flush(out, cho, jung, jong);
                        cho = c;
                        jung = 0;
                        jong = 0;
                    }
                }
            } else if (isVowel(c)) {
                if (jong != 0) {
                    // 받침이 다음 글자의 초성으로 넘어간다. 겹받침이면 뒤 키만 넘어간다.
                    String parts = SPLIT.get(jong);
                    char move;
                    if (parts != null) {
                        flush(out, cho, jung, parts.charAt(0));
                        move = parts.charAt(1);
                    } else {
                        flush(out, cho, jung, 0);
                        move = jong;
                    }
                    cho = move;
                    jung = c;
                    jong = 0;
                } else if (jung != 0) {
                    Character combined = COMBINED_VOWEL.get("" + jung + c);
                    if (combined != null) {
                        jung = combined;
                    } else {
                        flush(out, cho, jung, 0);
                        cho = 0;
                        jung = c;
                    }
                } else {
                    jung = c;
                }
            } else {
                flush(out, cho, jung, jong);
                cho = jung = jong = 0;
                out.append(c);
            }
        }
        flush(out, cho, jung, jong);
        return out.toString();
    }

    /** 모아 둔 초성·중성·종성을 글자로 내보낸다 (0 = 없음). 초성 없이 모음만 있으면 낱자로 내보낸다. */
    private static void flush(StringBuilder out, int cho, int jung, int jong) {
        if (cho != 0 && jung != 0) {
            int code = (CHO.indexOf(cho) * 21 + JUNG.indexOf(jung)) * 28 + (jong == 0 ? 0 : JONG.indexOf(jong));
            out.append((char) (BASE + code));
        } else {
            if (cho != 0) out.append((char) cho);
            if (jung != 0) out.append((char) jung);
        }
    }

    /**
     * 한/영 전환을 잊고 영문으로 친 입력을 한글로 되돌린다. 영문 글자가 없거나 결과에 완성형 한글이 없으면 null.
     * 예: "ekadid" → "담양", "rnrflqrhddnjs" → "국립공원"
     */
    public static String fromQwerty(String typed) {
        if (typed == null) return null;
        StringBuilder jamo = new StringBuilder(typed.length());
        boolean hasLetter = false;
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (c >= 0x80) return null; // 이미 한글 등이 섞여 있으면 전환 오타가 아니다
            Character j = QWERTY.get(c);
            if (j != null) hasLetter = true;
            jamo.append(j == null ? c : j);
        }
        if (!hasLetter) return null;
        String result = compose(jamo);
        for (int i = 0; i < result.length(); i++) {
            if (isSyllable(result.charAt(i))) return result;
        }
        return null;
    }

    /** 한글을 영문 자판 입력으로 바꾼다 (fromQwerty 의 반대, 테스트·시연용). */
    public static String toQwerty(String hangul) {
        Keys k = toKeys(hangul);
        StringBuilder sb = new StringBuilder(k.length());
        for (char c : k.keys) {
            Character q = TO_QWERTY.get(c);
            sb.append(q == null ? c : q);
        }
        return sb.toString();
    }
}
