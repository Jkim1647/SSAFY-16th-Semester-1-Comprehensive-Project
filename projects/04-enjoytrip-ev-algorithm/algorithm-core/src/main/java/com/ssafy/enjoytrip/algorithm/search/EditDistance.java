package com.ssafy.enjoytrip.algorithm.search;

/**
 * 편집 거리 (Damerau-Levenshtein 의 OSA 변형) 동적계획법.
 *
 * <p>dp[i][j] = 패턴 앞 i 글자를 본문 앞 j 글자에 맞추는 최소 편집 횟수.
 * <pre>
 * dp[i][j] = min( dp[i-1][j-1] + (같으면 0, 다르면 1)   교체
 *                 dp[i-1][j]   + 1                       패턴 글자 삭제
 *                 dp[i][j-1]   + 1                       글자 삽입
 *                 dp[i-2][j-2] + 1  (인접한 두 글자가 뒤바뀐 경우) 전치 )
 * </pre>
 * 시간 O(M·N), 메모리 O(M·N).
 *
 * <p>{@link #bestSubstring} 은 첫 행을 모두 0 으로 두어 "본문의 어느 부분 문자열과 가장 가까운가"를 구한다
 * (본문 어디서 시작해도 비용 0). 검색어가 관광지 이름의 일부일 때 쓴다.
 */
public final class EditDistance {

    private EditDistance() {
    }

    /** 두 문자열 전체의 OSA 거리 */
    public static int distance(char[] a, char[] b) {
        int m = a.length;
        int n = b.length;
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) d[i][0] = i;
        for (int j = 0; j <= n; j++) d[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int best = d[i - 1][j - 1] + (a[i - 1] == b[j - 1] ? 0 : 1);
                best = Math.min(best, d[i - 1][j] + 1);
                best = Math.min(best, d[i][j - 1] + 1);
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) best = Math.min(best, d[i - 2][j - 2] + 1);
                d[i][j] = best;
            }
        }
        return d[m][n];
    }

    /** 부분 문자열 매칭 결과: 거리와, 본문에서 맞춰진 구간 [start, end) */
    public static final class Match {
        public final int distance;
        public final int start;
        public final int end;

        Match(int distance, int start, int end) {
            this.distance = distance;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * 패턴과 가장 가까운 본문의 부분 문자열. 거리가 maxDistance 를 넘을 것이 확실해지면 계산을 멈추고 null.
     * (행의 최솟값은 아래로 갈수록 줄어들지 않으므로, 한 행의 최솟값이 maxDistance 를 넘으면 답도 넘는다)
     */
    public static Match bestSubstring(char[] pattern, char[] text, int maxDistance) {
        int m = pattern.length;
        int n = text.length;
        if (m == 0) return new Match(0, 0, 0);
        int[][] d = new int[m + 1][n + 1];
        // start[i][j] = 이 칸까지 오는 최적 경로가 본문의 어디서 시작했는지 (하이라이트 구간 복원용)
        int[][] start = new int[m + 1][n + 1];
        for (int j = 0; j <= n; j++) {
            d[0][j] = 0;
            start[0][j] = j;
        }
        for (int i = 1; i <= m; i++) {
            d[i][0] = i;
            start[i][0] = 0;
            int rowMin = d[i][0];
            for (int j = 1; j <= n; j++) {
                int best = d[i - 1][j - 1] + (pattern[i - 1] == text[j - 1] ? 0 : 1);
                int st = start[i - 1][j - 1];
                if (d[i - 1][j] + 1 < best) {
                    best = d[i - 1][j] + 1;
                    st = start[i - 1][j];
                }
                if (d[i][j - 1] + 1 < best) {
                    best = d[i][j - 1] + 1;
                    st = start[i][j - 1];
                }
                if (i > 1 && j > 1 && pattern[i - 1] == text[j - 2] && pattern[i - 2] == text[j - 1] && d[i - 2][j - 2] + 1 < best) {
                    best = d[i - 2][j - 2] + 1;
                    st = start[i - 2][j - 2];
                }
                d[i][j] = best;
                start[i][j] = st;
                rowMin = Math.min(rowMin, best);
            }
            if (rowMin > maxDistance) return null;
        }
        int bestJ = 0;
        for (int j = 1; j <= n; j++) {
            if (d[m][j] < d[m][bestJ]) bestJ = j;
        }
        if (d[m][bestJ] > maxDistance) return null;
        return new Match(d[m][bestJ], start[m][bestJ], bestJ);
    }
}
