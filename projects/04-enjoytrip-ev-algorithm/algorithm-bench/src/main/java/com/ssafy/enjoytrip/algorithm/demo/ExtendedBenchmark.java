package com.ssafy.enjoytrip.algorithm.demo;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlanner;
import com.ssafy.enjoytrip.algorithm.geo.DistanceCalculator;
import com.ssafy.enjoytrip.algorithm.geo.GridIndex;
import com.ssafy.enjoytrip.algorithm.geo.HaversineDistance;
import com.ssafy.enjoytrip.algorithm.geo.KakaoRoadDistance;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.recommend.SpotRecommender;
import com.ssafy.enjoytrip.algorithm.route.RouteOptimizer;
import com.ssafy.enjoytrip.algorithm.search.AutocompleteTrie;
import com.ssafy.enjoytrip.algorithm.search.FuzzySearcher;
import com.ssafy.enjoytrip.algorithm.search.Hangul;
import com.ssafy.enjoytrip.algorithm.search.KeywordSearcher;
import com.ssafy.enjoytrip.algorithm.search.KmpMatcher;
import com.ssafy.enjoytrip.algorithm.search.KoreanText;
import com.ssafy.enjoytrip.algorithm.security.PasswordHasher;
import com.ssafy.enjoytrip.algorithm.sort.TimSort;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 알고리즘 #3~#9 성능 측정. 실행: sh scripts/run.sh bench2
 * 카카오 도로 거리 비교는 환경 변수 KAKAO_REST_KEY 가 있을 때만 실행한다.
 */
public class ExtendedBenchmark {

    public static void main(String[] args) throws Exception {
        Path dataDir = Paths.get(args.length > 0 ? args[0] : "data");
        List<Attraction> attractions = CsvDataLoader.attractionDetails(dataDir.resolve("attractions-gwangju-jeonnam.csv"));
        List<ChargingStation> stations = CsvDataLoader.stations(dataDir.resolve("ev-stations-gwangju-jeonnam.csv"));
        System.out.println("Java " + System.getProperty("java.version"));
        System.out.println();

        password();
        autocomplete(attractions);
        kmp(attractions);
        timSort();
        gridAndRecommend(attractions, stations);
        fuzzy(attractions);
        roadDistance(attractions);
    }

    static void password() {
        System.out.println("## #3 비밀번호 해시: 반복 횟수(키 스트레칭)에 따른 해시 1회 시간");
        System.out.println("| 반복 횟수 | 해시 1회 (ms) | 공격자가 CPU 1코어로 1초에 시도할 수 있는 비밀번호 수 |");
        System.out.println("|---:|---:|---:|");
        for (int it : new int[]{1, 1_000, 100_000, 600_000}) {
            final PasswordHasher h = new PasswordHasher("pepper".getBytes(), it, new SecureRandom());
            double ms = AlgorithmBenchmark.medianMs(3, () -> h.hash("ssafy1234!"));
            System.out.printf("| %,d | %.3f | 약 %,.0f개 |%n", it, ms, 1000 / ms);
        }
        System.out.println();
    }

    static void autocomplete(List<Attraction> attractions) {
        System.out.println("## #4 자동완성: Trie(노드별 Top-K) vs 매번 전체 검사");
        long t0 = System.nanoTime();
        final AutocompleteTrie<Attraction> trie = new AutocompleteTrie<Attraction>(10);
        for (Attraction a : attractions) trie.add(a.getName(), a, 0);
        double buildMs = (System.nanoTime() - t0) / 1e6;

        Random rnd = new Random(1);
        final List<String> queries = new ArrayList<String>();
        for (int i = 0; i < 2000; i++) {
            String name = KoreanText.normalize(attractions.get(rnd.nextInt(attractions.size())).getName());
            String q = name.substring(0, Math.min(name.length(), 1 + rnd.nextInt(3)));
            queries.add(i % 4 == 0 ? KoreanText.toChoseong(q) : q);
        }
        double trieMs = AlgorithmBenchmark.medianMs(5, () -> {
            int n = 0;
            for (String q : queries) n += trie.suggest(q, 10).size();
            return n;
        });
        final List<String> normalized = new ArrayList<String>();
        final List<String> choseong = new ArrayList<String>();
        for (Attraction a : attractions) {
            normalized.add(KoreanText.normalize(a.getName()));
            choseong.add(KoreanText.toChoseong(KoreanText.normalize(a.getName())));
        }
        double scanMs = AlgorithmBenchmark.medianMs(5, () -> {
            int n = 0;
            for (String q : queries) {
                List<String> hit = new ArrayList<String>();
                boolean cho = KoreanText.isChoseongOnly(q);
                for (int i = 0; i < normalized.size(); i++) {
                    if ((cho ? choseong : normalized).get(i).startsWith(q)) hit.add(normalized.get(i));
                }
                Collections.sort(hit);
                n += Math.min(10, hit.size());
            }
            return n;
        });
        System.out.printf("관광지 %,d곳, 노드 %,d개 (일반+초성), 만들기 %.1f ms%n", attractions.size(), trie.nodeCount(), buildMs);
        System.out.println("| 방식 | 검색어 2,000개 (ms) | 검색어 1개 평균 (µs) | 복잡도 |");
        System.out.println("|---|---:|---:|---|");
        System.out.printf("| Trie + 노드별 Top-K | %.2f | %.2f | O(P + K) |%n", trieMs, trieMs * 1000 / queries.size());
        System.out.printf("| 전체 검사 + 정렬 | %.2f | %.2f | O(N·P + M log M) |%n", scanMs, scanMs * 1000 / queries.size());
        System.out.println();
    }

    static void kmp(List<Attraction> attractions) {
        System.out.println("## #5 KMP vs 단순 비교 (문자 비교가 많이 겹치는 최악 입력과 실제 주소 데이터)");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1_000_000; i++) sb.append('a');
        final String text = sb.toString();
        StringBuilder pb = new StringBuilder();
        for (int i = 0; i < 1000; i++) pb.append('a');
        final String pattern = pb.append('b').toString();

        StringBuilder corpus = new StringBuilder();
        for (Attraction a : attractions) corpus.append(a.getName()).append(' ').append(a.getAddr()).append('\n');
        final String real = corpus.toString();

        System.out.println("| 입력 | 본문 길이 N | 패턴 길이 M | KMP (ms) | 단순 비교 (ms) | String.indexOf 반복 (ms) |");
        System.out.println("|---|---:|---:|---:|---:|---:|");
        bench("최악: a…a 에서 a…ab 찾기", text, pattern);
        bench("실제: 관광지 이름+주소에서 \"국립공원\"", real, "국립공원");
        System.out.println();
    }

    private static void bench(String label, final String text, final String pattern) {
        final KmpMatcher kmp = new KmpMatcher(pattern);
        double k = AlgorithmBenchmark.medianMs(3, () -> kmp.findAll(text).size());
        double n = AlgorithmBenchmark.medianMs(3, () -> naive(text, pattern));
        double j = AlgorithmBenchmark.medianMs(3, () -> {
            int c = 0;
            for (int i = text.indexOf(pattern); i >= 0; i = text.indexOf(pattern, i + 1)) c++;
            return c;
        });
        System.out.printf("| %s | %,d | %,d | %.2f | %.2f | %.2f |%n", label, text.length(), pattern.length(), k, n, j);
    }

    private static int naive(String text, String pattern) {
        int count = 0;
        for (int i = 0; i + pattern.length() <= text.length(); i++) {
            int j = 0;
            while (j < pattern.length() && text.charAt(i + j) == pattern.charAt(j)) j++;
            if (j == pattern.length()) count++;
        }
        return count;
    }

    static void timSort() {
        System.out.println("## #6 정렬 비교 (Integer 100,000개, 비교 횟수 / 시간)");
        System.out.println("| 입력 | TimSort (직접 구현) | JDK Arrays.sort (TimSort) | 단순 병합 정렬 |");
        System.out.println("|---|---:|---:|---:|");
        Random rnd = new Random(7);
        int n = 100_000;
        String[] labels = {"무작위", "정렬됨", "역순", "앞 90% 정렬 + 뒤 10% 무작위", "중복 많음 (값 10종)"};
        for (int p = 0; p < labels.length; p++) {
            final Integer[] base = new Integer[n];
            for (int i = 0; i < n; i++) {
                switch (p) {
                    case 0: base[i] = rnd.nextInt(); break;
                    case 1: base[i] = i; break;
                    case 2: base[i] = n - i; break;
                    case 3: base[i] = i < n * 9 / 10 ? i : rnd.nextInt(n); break;
                    default: base[i] = rnd.nextInt(10);
                }
            }
            String a = measureSort(base, 0);
            String b = measureSort(base, 1);
            String c = measureSort(base, 2);
            System.out.printf("| %s | %s | %s | %s |%n", labels[p], a, b, c);
        }
        System.out.println();
    }

    private static String measureSort(final Integer[] base, final int which) {
        final long[] cmp = {0};
        final Comparator<Integer> counting = new Comparator<Integer>() {
            @Override
            public int compare(Integer x, Integer y) {
                cmp[0]++;
                return x.compareTo(y);
            }
        };
        double ms = AlgorithmBenchmark.medianMs(3, () -> {
            Integer[] a = base.clone();
            cmp[0] = 0;
            if (which == 0) TimSort.sort(a, counting);
            else if (which == 1) Arrays.sort(a, counting);
            else mergeSort(a, new Integer[a.length], 0, a.length, counting);
            return a;
        });
        return String.format("%,d회 / %.1f ms", cmp[0], ms);
    }

    private static void mergeSort(Integer[] a, Integer[] tmp, int lo, int hi, Comparator<Integer> c) {
        if (hi - lo < 2) return;
        int mid = (lo + hi) >>> 1;
        mergeSort(a, tmp, lo, mid, c);
        mergeSort(a, tmp, mid, hi, c);
        int i = lo, j = mid, k = lo;
        while (i < mid && j < hi) tmp[k++] = c.compare(a[j], a[i]) < 0 ? a[j++] : a[i++];
        while (i < mid) tmp[k++] = a[i++];
        while (j < hi) tmp[k++] = a[j++];
        System.arraycopy(tmp, lo, a, lo, hi - lo);
    }

    static void gridAndRecommend(final List<Attraction> attractions, List<ChargingStation> stations) {
        System.out.println("## #7 추천: 관광지별 반경 3km 급속 충전소 수 계산 (격자 인덱스 vs 전체 비교) + 추천 시간");
        final List<ChargingStation> fast = new ArrayList<ChargingStation>();
        for (ChargingStation s : stations) if (s.getMaxKw() >= EvRoutePlanner.DEFAULT_MIN_CHARGER_KW) fast.add(s);
        long t0 = System.nanoTime();
        final GridIndex<ChargingStation> grid = new GridIndex<ChargingStation>(fast, 2.0, 35);
        double buildMs = (System.nanoTime() - t0) / 1e6;
        double gridMs = AlgorithmBenchmark.medianMs(3, () -> {
            int sum = 0;
            for (Attraction a : attractions) sum += grid.countWithin(a.getLat(), a.getLng(), 3);
            return sum;
        });
        double bruteMs = AlgorithmBenchmark.medianMs(3, () -> {
            int sum = 0;
            for (Attraction a : attractions) {
                for (ChargingStation s : fast) {
                    if (HaversineDistance.greatCircleKm(a.getLat(), a.getLng(), s.getLat(), s.getLng()) <= 3) sum++;
                }
            }
            return sum;
        });
        System.out.println("| 방식 | 관광지 " + attractions.size() + "곳 × 급속 충전소 " + fast.size() + "곳 (ms) | 복잡도 |");
        System.out.println("|---|---:|---|");
        System.out.printf("| 격자 인덱스 (칸 2km, 만들기 %.1f ms 별도) | %.2f | O(N · 주변 칸의 충전소 수) |%n", buildMs, gridMs);
        System.out.printf("| 전체 비교 | %.2f | O(N · M) |%n", bruteMs);

        final SpotRecommender<Attraction> rec = new SpotRecommender<Attraction>(attractions,
                new SpotRecommender.Features<Attraction>() {
                    @Override
                    public Collection<String> of(Attraction item) {
                        return item.categoryCodes();
                    }
                }, grid, SpotRecommender.Weights.defaults());
        Map<String, Double> prefer = new HashMap<String, Double>();
        prefer.put("HS", 1.0);
        final SpotRecommender.Preference pref = new SpotRecommender.Preference(
                Arrays.asList(attractions.get(0).getId(), attractions.get(5).getId()), prefer,
                new Location("b", "광주송정역", 35.1378, 126.7932), null);
        double topK = AlgorithmBenchmark.medianMs(5, () -> rec.recommend(pref, 10));
        double full = AlgorithmBenchmark.medianMs(5, () -> rec.recommend(pref, attractions.size()));
        System.out.printf("%n추천 1회 (관광지 %,d곳): Top-10 힙 %.3f ms, 전체 순위 정렬 %.3f ms%n%n", attractions.size(), topK, full);
    }

    /**
     * #9 오타 허용 검색: 실제 관광지 이름 일부에 자모 오타 1개를 넣은 검색어로
     * KMP(정확 일치)와 오타 허용 검색의 찾기 성공률, 3-gram 필터 효과(후보 수·시간)를 잰다.
     */
    static void fuzzy(final List<Attraction> attractions) {
        System.out.println("## #9 오타 허용 검색: 이름 일부(3~6글자)에 자모 오타 1개를 넣은 검색어 1,000개");
        KeywordSearcher.Fields<Attraction> fields = new KeywordSearcher.Fields<Attraction>() {
            @Override
            public String[] of(Attraction a) {
                return new String[]{a.getName(), a.getAddr()};
            }
        };
        final FuzzySearcher<Attraction> fuzzy = new FuzzySearcher<Attraction>(attractions, fields);
        final KeywordSearcher<Attraction> kmp = new KeywordSearcher<Attraction>(fields);
        Random rnd = new Random(9);
        final List<String> queries = new ArrayList<String>();
        final List<Attraction> targets = new ArrayList<Attraction>();
        while (queries.size() < 1000) {
            Attraction a = attractions.get(rnd.nextInt(attractions.size()));
            String name = KoreanText.normalize(a.getName());
            if (name.length() < 3) continue;
            int len = Math.min(name.length(), 3 + rnd.nextInt(4));
            int from = rnd.nextInt(name.length() - len + 1);
            String typo = typo(rnd, name.substring(from, from + len));
            if (typo == null) continue;
            queries.add(typo);
            targets.add(a);
        }
        int kmpFound = 0;
        int fuzzyFound = 0;
        int fuzzyTop = 0;
        long candidates = 0;
        for (int i = 0; i < queries.size(); i++) {
            for (KeywordSearcher.Match<Attraction> m : kmp.search(attractions, queries.get(i), 1000)) {
                if (m.getItem() == targets.get(i)) {
                    kmpFound++;
                    break;
                }
            }
            FuzzySearcher.Result<Attraction> r = fuzzy.search(queries.get(i), 10);
            candidates += r.getCandidates();
            int best = r.getMatches().isEmpty() ? -1 : r.getMatches().get(0).getDistance();
            for (FuzzySearcher.Match<Attraction> m : r.getMatches()) {
                if (m.getItem() == targets.get(i)) {
                    fuzzyFound++;
                    if (m.getDistance() == best) fuzzyTop++;
                    break;
                }
            }
        }
        final List<Hangul.Keys> keys = new ArrayList<Hangul.Keys>();
        for (String q : queries) keys.add(Hangul.toKeys(q));
        double withIndex = AlgorithmBenchmark.medianMs(3, () -> {
            int n = 0;
            for (Hangul.Keys k : keys) n += fuzzy.search(k, FuzzySearcher.defaultMaxDistance(k.length()), 10, true).getMatches().size();
            return n;
        });
        double withoutIndex = AlgorithmBenchmark.medianMs(3, () -> {
            int n = 0;
            for (Hangul.Keys k : keys) n += fuzzy.search(k, FuzzySearcher.defaultMaxDistance(k.length()), 10, false).getMatches().size();
            return n;
        });
        System.out.println("| 방법 | 원래 관광지를 찾은 비율 |");
        System.out.println("|---|---:|");
        System.out.printf("| KMP 정확 일치 | %.1f%% |%n", kmpFound / 10.0);
        System.out.printf("| 오타 허용 검색 (상위 10개 안) | %.1f%% |%n", fuzzyFound / 10.0);
        System.out.printf("| 오타 허용 검색 (가장 가까운 거리 묶음 안) | %.1f%% |%n", fuzzyTop / 10.0);
        System.out.println();
        System.out.println("| 방식 | 검색어 1,000개 (ms) | 검색어 1개 평균 (ms) | 평균 비교 항목 수 |");
        System.out.println("|---|---:|---:|---:|");
        System.out.printf("| 3-gram 필터 + 편집 거리 | %.1f | %.3f | %,d |%n", withIndex, withIndex / 1000, candidates / 1000);
        System.out.printf("| 전체 항목 편집 거리 | %.1f | %.3f | %,d |%n", withoutIndex, withoutIndex / 1000, attractions.size());
        System.out.println();
    }

    /** 자모 키 하나를 바꾸거나 지우거나 이웃과 맞바꾼 검색어. 결과가 원래와 같으면 null */
    private static String typo(Random rnd, String s) {
        char[] k = Hangul.toKeys(s).keys;
        if (k.length < 2) return null;
        StringBuilder sb = new StringBuilder(new String(k));
        int p = rnd.nextInt(k.length - 1);
        switch (rnd.nextInt(3)) {
            case 0:
                sb.setCharAt(p, "ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅏㅓㅗㅜㅡㅣ".charAt(rnd.nextInt(15)));
                break;
            case 1:
                sb.deleteCharAt(p);
                break;
            default:
                char c = sb.charAt(p);
                sb.setCharAt(p, sb.charAt(p + 1));
                sb.setCharAt(p + 1, c);
        }
        String result = Hangul.compose(sb);
        return result.equals(s) ? null : result;
    }

    static void roadDistance(List<Attraction> attractions) {
        String key = System.getenv("KAKAO_REST_KEY");
        System.out.println("## #8 도로 거리: 카카오모빌리티 실제 도로 거리 vs 직선 × 1.3 근사 (시연 관광지 10곳 + 출발지)");
        if (key == null || key.trim().isEmpty()) {
            System.out.println("KAKAO_REST_KEY 가 없어 건너뜀");
            return;
        }
        Map<String, Attraction> byId = new HashMap<String, Attraction>();
        for (Attraction a : attractions) byId.put(a.getId(), a);
        List<Location> nodes = new ArrayList<Location>();
        nodes.add(AlgorithmDemo.START);
        for (String id : AlgorithmDemo.PLAN_IDS) nodes.add(byId.get(id));

        KakaoRoadDistance road = new KakaoRoadDistance(key);
        HaversineDistance straight = new HaversineDistance(1.0);
        List<Double> ratios = new ArrayList<Double>();
        double sumAbsErr = 0;
        int pairs = 0;
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                double r = road.km(nodes.get(i), nodes.get(j));
                double s = straight.km(nodes.get(i), nodes.get(j));
                ratios.add(r / s);
                sumAbsErr += Math.abs(s * 1.3 - r) / r;
                pairs++;
            }
        }
        Collections.sort(ratios);
        System.out.printf("API 호출 %d회 (실패 → 대체 %d회), 도로/직선 비율: 최소 %.2f, 중앙값 %.2f, 최대 %.2f, ×1.3 근사의 평균 오차 %.1f%%%n",
                road.getApiCalls(), road.getFallbackCount(), ratios.get(0), ratios.get(ratios.size() / 2),
                ratios.get(ratios.size() - 1), sumAbsErr / pairs * 100);

        // 직선 근사로 정한 순서를 실제 도로 거리로 쟀을 때 vs 도로 거리로 정한 순서
        List<Location> stops = nodes.subList(1, nodes.size());
        List<Location> byStraight = new RouteOptimizer(new HaversineDistance()).optimize(nodes.get(0), stops, true).getOrderedStops();
        List<Location> byRoad = new RouteOptimizer(road).optimize(nodes.get(0), stops, true).getOrderedStops();
        System.out.printf("순서 최적화 결과를 도로 거리로 측정: 직선 근사로 정한 순서 %.1f km, 도로 거리로 정한 순서 %.1f km%n",
                roadLength(road, nodes.get(0), byStraight), roadLength(road, nodes.get(0), byRoad));
        System.out.println("도로 거리로 정한 순서: " + names(byRoad));
        System.out.println();
    }

    private static double roadLength(DistanceCalculator d, Location start, List<Location> order) {
        double sum = 0;
        Location prev = start;
        for (Location l : order) {
            sum += d.km(prev, l);
            prev = l;
        }
        return sum + d.km(prev, start);
    }

    private static String names(List<Location> list) {
        StringBuilder sb = new StringBuilder();
        for (Location l : list) sb.append(sb.length() == 0 ? "" : " → ").append(l.getName());
        return sb.toString();
    }
}
