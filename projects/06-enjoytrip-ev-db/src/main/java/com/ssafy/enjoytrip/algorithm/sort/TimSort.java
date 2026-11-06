package com.ssafy.enjoytrip.algorithm.sort;

import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

/**
 * TimSort (Tim Peters, 2002) 를 수업 내용에 맞춰 직접 구현한 하이브리드 안정 정렬.
 *
 * <ol>
 *   <li>자연 run 탐지: 이미 오름차순인 구간은 그대로, 엄격한 내림차순 구간은 뒤집어서 run 으로 쓴다 (실제 데이터는 부분 정렬된 경우가 많다).</li>
 *   <li>minRun: run 이 너무 짧으면 minRun(16~32) 길이까지 늘린다. 늘린 부분은 이진 삽입 정렬로 정렬한다
 *       (삽입 위치 = 정렬된 구간에서 upper bound → 같은 값은 뒤에 들어가 안정성 유지).</li>
 *   <li>run 스택: 쌓인 run 길이가 A > B + C, B > C 를 만족하도록 유지하고, 깨지면 인접한 두 run 을 병합한다.
 *       비슷한 크기끼리 병합되어 병합 비용이 균형을 이룬다.</li>
 *   <li>병합: 왼쪽 run 에서 오른쪽 run 의 첫 원소보다 작거나 같은 앞부분(upper bound), 오른쪽 run 에서 왼쪽 run 의
 *       마지막 원소보다 작은 뒷부분(lower bound)은 이미 제자리이므로 건너뛰고, 남은 부분만 임시 배열로 병합한다.</li>
 * </ol>
 * JDK 구현의 galloping(지수 탐색) 모드는 생략했다. 시간 최악·평균 O(n log n), 이미 정렬된 입력은 O(n), 추가 메모리 O(n).
 */
public final class TimSort {

    static final int MIN_MERGE = 32;

    private TimSort() {
    }

    @SuppressWarnings("unchecked")
    public static <T> void sort(List<T> list, Comparator<? super T> c) {
        Object[] a = list.toArray();
        sort((T[]) a, c);
        ListIterator<T> it = list.listIterator();
        for (Object e : a) {
            it.next();
            it.set((T) e);
        }
    }

    public static <T> void sort(T[] a, Comparator<? super T> c) {
        if (a == null || c == null) throw new IllegalArgumentException("배열과 Comparator 가 필요합니다.");
        int n = a.length;
        if (n < 2) return;
        if (n < MIN_MERGE) {
            int run = countRunAndMakeAscending(a, 0, n, c);
            binaryInsertionSort(a, 0, n, run, c);
            return;
        }
        new Sorter<T>(a, c).run();
    }

    /** n 을 2의 거듭제곱으로 나누다 보면 run 개수가 2의 거듭제곱에 가까워져 병합이 균형을 이룬다. 결과는 MIN_MERGE/2 ~ MIN_MERGE */
    static int minRunLength(int n) {
        int r = 0;
        while (n >= MIN_MERGE) {
            r |= n & 1;
            n >>= 1;
        }
        return n + r;
    }

    /** lo 부터 시작하는 run 의 길이. 엄격한 내림차순이면 뒤집는다 (같은 값이 있는 구간을 뒤집으면 안정성이 깨지므로 엄격). */
    static <T> int countRunAndMakeAscending(T[] a, int lo, int hi, Comparator<? super T> c) {
        int runHi = lo + 1;
        if (runHi == hi) return 1;
        if (c.compare(a[runHi++], a[lo]) < 0) {
            while (runHi < hi && c.compare(a[runHi], a[runHi - 1]) < 0) runHi++;
            reverse(a, lo, runHi);
        } else {
            while (runHi < hi && c.compare(a[runHi], a[runHi - 1]) >= 0) runHi++;
        }
        return runHi - lo;
    }

    /** [lo, start) 는 이미 정렬되어 있다. [start, hi) 의 원소를 하나씩 upper bound 위치에 끼워 넣는다. */
    static <T> void binaryInsertionSort(T[] a, int lo, int hi, int start, Comparator<? super T> c) {
        if (start == lo) start++;
        for (; start < hi; start++) {
            T pivot = a[start];
            int left = lo;
            int right = start;
            while (left < right) {
                int mid = (left + right) >>> 1;
                if (c.compare(pivot, a[mid]) < 0) right = mid;
                else left = mid + 1;
            }
            System.arraycopy(a, left, a, left + 1, start - left);
            a[left] = pivot;
        }
    }

    private static void reverse(Object[] a, int lo, int hi) {
        hi--;
        while (lo < hi) {
            Object t = a[lo];
            a[lo++] = a[hi];
            a[hi--] = t;
        }
    }

    private static final class Sorter<T> {
        private final T[] a;
        private final Comparator<? super T> c;
        private final int[] runBase;
        private final int[] runLen;
        private int stackSize;
        private Object[] tmp;

        Sorter(T[] a, Comparator<? super T> c) {
            this.a = a;
            this.c = c;
            // 불변식 덕분에 run 길이가 피보나치 수열 이상으로 커지므로 스택 깊이는 O(log n). int 범위에서 49 면 충분하다.
            this.runBase = new int[49];
            this.runLen = new int[49];
            this.tmp = new Object[Math.min(a.length / 2 + 1, 256)];
        }

        void run() {
            int lo = 0;
            int remaining = a.length;
            int minRun = minRunLength(remaining);
            while (remaining > 0) {
                int len = countRunAndMakeAscending(a, lo, a.length, c);
                if (len < minRun) {
                    int force = Math.min(remaining, minRun);
                    binaryInsertionSort(a, lo, lo + force, lo + len, c);
                    len = force;
                }
                runBase[stackSize] = lo;
                runLen[stackSize] = len;
                stackSize++;
                mergeCollapse();
                lo += len;
                remaining -= len;
            }
            while (stackSize > 1) {
                int n = stackSize - 2;
                if (n > 0 && runLen[n - 1] < runLen[n + 1]) n--;
                mergeAt(n);
            }
        }

        /** 스택 위쪽 run 들이 A > B + C, B > C 를 만족할 때까지 병합 (JDK 수정판 조건: 두 단계 아래까지 검사) */
        private void mergeCollapse() {
            while (stackSize > 1) {
                int n = stackSize - 2;
                if ((n > 0 && runLen[n - 1] <= runLen[n] + runLen[n + 1])
                        || (n > 1 && runLen[n - 2] <= runLen[n - 1] + runLen[n])) {
                    if (runLen[n - 1] < runLen[n + 1]) n--;
                } else if (runLen[n] > runLen[n + 1]) {
                    break;
                }
                mergeAt(n);
            }
        }

        private void mergeAt(int i) {
            int base1 = runBase[i];
            int len1 = runLen[i];
            int base2 = runBase[i + 1];
            int len2 = runLen[i + 1];
            runLen[i] = len1 + len2;
            if (i == stackSize - 3) {
                runBase[i + 1] = runBase[i + 2];
                runLen[i + 1] = runLen[i + 2];
            }
            stackSize--;

            // 왼쪽 run 앞부분 중 오른쪽 첫 원소 이하인 원소들은 이미 제자리 (upper bound)
            int k = upperBound(a[base2], base1, len1);
            base1 += k;
            len1 -= k;
            if (len1 == 0) return;
            // 오른쪽 run 뒷부분 중 왼쪽 마지막 원소 이상인 원소들도 제자리 (lower bound)
            len2 = lowerBound(a[base1 + len1 - 1], base2, len2) - base2;
            if (len2 == 0) return;
            mergeLo(base1, len1, base2, len2);
        }

        /** 왼쪽 run 을 임시 배열로 옮긴 뒤 앞에서부터 병합. 같으면 왼쪽을 먼저 넣어 안정 정렬 */
        @SuppressWarnings("unchecked")
        private void mergeLo(int base1, int len1, int base2, int len2) {
            if (tmp.length < len1) tmp = new Object[Math.max(len1, Math.min(a.length, tmp.length * 2))];
            System.arraycopy(a, base1, tmp, 0, len1);
            int i = 0;
            int j = base2;
            int end2 = base2 + len2;
            int dest = base1;
            while (i < len1 && j < end2) {
                if (c.compare(a[j], (T) tmp[i]) < 0) a[dest++] = a[j++];
                else a[dest++] = (T) tmp[i++];
            }
            if (i < len1) System.arraycopy(tmp, i, a, dest, len1 - i);
            for (int t = 0; t < len1; t++) tmp[t] = null;
        }

        /** a[base, base+len) 에서 key 보다 큰 첫 위치까지의 개수 */
        private int upperBound(T key, int base, int len) {
            int lo = base;
            int hi = base + len;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (c.compare(key, a[mid]) < 0) hi = mid;
                else lo = mid + 1;
            }
            return lo - base;
        }

        /** a[base, base+len) 에서 key 이상인 첫 위치 (절대 인덱스) */
        private int lowerBound(T key, int base, int len) {
            int lo = base;
            int hi = base + len;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (c.compare(a[mid], key) < 0) lo = mid + 1;
                else hi = mid;
            }
            return lo;
        }
    }
}
