package com.ssafy.enjoytrip.algorithm;

import com.ssafy.enjoytrip.algorithm.geo.GridIndex;
import com.ssafy.enjoytrip.algorithm.geo.HaversineDistance;
import com.ssafy.enjoytrip.algorithm.geo.KakaoRoadDistance;
import com.ssafy.enjoytrip.algorithm.geo.KakaoRoadDistanceAccess;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.recommend.SpotRecommender;
import com.ssafy.enjoytrip.algorithm.search.AutocompleteTrie;
import com.ssafy.enjoytrip.algorithm.search.EditDistance;
import com.ssafy.enjoytrip.algorithm.search.FuzzySearcher;
import com.ssafy.enjoytrip.algorithm.search.Hangul;
import com.ssafy.enjoytrip.algorithm.search.KeywordSearcher;
import com.ssafy.enjoytrip.algorithm.search.KmpMatcher;
import com.ssafy.enjoytrip.algorithm.search.KoreanText;
import com.ssafy.enjoytrip.algorithm.security.PasswordHasher;
import com.ssafy.enjoytrip.algorithm.security.Pbkdf2;
import com.ssafy.enjoytrip.algorithm.sort.TimSort;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
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

import static com.ssafy.enjoytrip.algorithm.AlgorithmTest.check;
import static com.ssafy.enjoytrip.algorithm.AlgorithmTest.expectIae;
import static com.ssafy.enjoytrip.algorithm.AlgorithmTest.run;

/** 알고리즘 #3~#8 테스트. AlgorithmTest.main 에서 함께 실행된다. */
final class ExtendedAlgorithmTest {

    private ExtendedAlgorithmTest() {
    }

    static void runAll() {
        run("PBKDF2: RFC 7914 테스트 벡터와 같다 (반복 1회, 80,000회)", ExtendedAlgorithmTest::pbkdf2RfcVectors);
        run("PBKDF2: 직접 구현이 JDK SecretKeyFactory 결과와 같다 (무작위 200건)", ExtendedAlgorithmTest::pbkdf2MatchesJdk);
        run("비밀번호: 해시·검증, 같은 비밀번호도 salt 로 해시가 다르고 평문이 남지 않는다", ExtendedAlgorithmTest::passwordHashVerify);
        run("비밀번호: pepper 가 다르면 검증 실패, 재해시 판단, 깨진 형식·과도한 반복 횟수는 예외", ExtendedAlgorithmTest::passwordPepperAndRehash);
        run("Trie: 점수순 Top-K, 단어 단위·초성 검색, 같은 항목 중복 없음", ExtendedAlgorithmTest::trieBasics);
        run("Trie: 무작위 이름 2,000개에서 결과가 완전탐색(필터+정렬)과 같다", ExtendedAlgorithmTest::trieMatchesBruteForce);
        run("KMP: 실패 함수와 겹치는 등장 위치", ExtendedAlgorithmTest::kmpBasics);
        run("KMP: 무작위 문자열 2,000건에서 단순 비교 결과와 같다", ExtendedAlgorithmTest::kmpMatchesNaive);
        run("키워드 검색: 대소문자 무시, 이름 우선, 하이라이트 위치", ExtendedAlgorithmTest::keywordSearcher);
        run("TimSort: 여러 패턴·크기에서 Arrays.sort 와 같고 안정 정렬이다", ExtendedAlgorithmTest::timSortMatchesJdk);
        run("TimSort: 이미 정렬된 입력은 비교 n-1 번 (자연 run 활용)", ExtendedAlgorithmTest::timSortSortedIsLinear);
        run("격자 인덱스: 반경 검색과 최근접 k 가 완전탐색과 같다", ExtendedAlgorithmTest::gridMatchesBruteForce);
        run("추천: 취향이 같은 분류가 위로, 찜한 곳은 제외, Top-K 힙 = 전체 정렬", ExtendedAlgorithmTest::recommenderBasics);
        run("추천: 충전 가중치만 주면 충전소 많은 순, 기준 위치 없이 거리 가중치만 주면 입력 오류", ExtendedAlgorithmTest::recommenderChargerOnly);
        run("카카오 도로 거리: 응답 파싱과 실패 코드 처리", ExtendedAlgorithmTest::kakaoParse);
        run("한글 자모: 완성형 11,172자 분해→조합이 원래 글자, 무작위 문자열 2,000개도 그대로", ExtendedAlgorithmTest::hangulRoundTrip);
        run("한/영 전환 오타: ekadid→담양, 무작위 한글 2,000개 영타 변환 후 복원", ExtendedAlgorithmTest::qwertyConversion);
        run("편집 거리: 알려진 값, 부분 문자열 최소 거리가 완전탐색과 같다 (무작위 3,000건)", ExtendedAlgorithmTest::editDistanceMatchesBruteForce);
        run("오타 검색: 3-gram 필터 결과가 전체 비교와 같다 (무작위 오타 500건), 실제 예시", ExtendedAlgorithmTest::fuzzySearchFilterIsExact);
    }

    // ---------------------------------------------------------------- #3 비밀번호

    static void pbkdf2RfcVectors() {
        check(hex(Pbkdf2.derive("HmacSHA256", bytes("passwd"), bytes("salt"), 1, 64)).equals(
                "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"),
                "c=1");
        check(hex(Pbkdf2.derive("HmacSHA256", bytes("Password"), bytes("NaCl"), 80000, 64)).equals(
                "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d"),
                "c=80000");
    }

    static void pbkdf2MatchesJdk() throws Exception {
        Random rnd = new Random(5);
        SecretKeyFactory jdk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        for (int t = 0; t < 200; t++) {
            char[] pw = new char[1 + rnd.nextInt(30)];
            for (int i = 0; i < pw.length; i++) pw[i] = (char) (33 + rnd.nextInt(94));
            byte[] salt = new byte[1 + rnd.nextInt(24)]; // JDK PBEKeySpec 은 빈 salt 를 받지 않는다
            rnd.nextBytes(salt);
            int iterations = 1 + rnd.nextInt(50);
            int dkLen = 1 + rnd.nextInt(80);
            byte[] expected = jdk.generateSecret(new PBEKeySpec(pw, salt, iterations, dkLen * 8)).getEncoded();
            byte[] actual = Pbkdf2.derive("HmacSHA256", new String(pw).getBytes(StandardCharsets.UTF_8), salt, iterations, dkLen);
            check(Arrays.equals(expected, actual), "t=" + t);
        }
    }

    static void passwordHashVerify() {
        PasswordHasher hasher = new PasswordHasher("pepper-1".getBytes(StandardCharsets.UTF_8), 1000, new SecureRandom());
        String stored = hasher.hash("ssafy1234!");
        check(stored.startsWith("pbkdf2_sha256$1000$"), "형식 " + stored);
        check(!stored.contains("ssafy1234"), "평문 노출");
        check(hasher.verify("ssafy1234!", stored), "맞는 비밀번호");
        check(!hasher.verify("ssafy1234?", stored), "틀린 비밀번호");
        check(!hasher.verify("Ssafy1234!", stored), "대소문자 구분");
        check(!stored.equals(hasher.hash("ssafy1234!")), "같은 비밀번호라도 salt 가 달라 해시가 달라야 함");
        check(hasher.verify("한글비밀번호", hasher.hash("한글비밀번호")), "한글");
        expectIae(() -> hasher.hash(""));
        expectIae(() -> hasher.hash(null));
    }

    static void passwordPepperAndRehash() {
        PasswordHasher a = new PasswordHasher("pepper-A".getBytes(StandardCharsets.UTF_8), 1000, new SecureRandom());
        PasswordHasher b = new PasswordHasher("pepper-B".getBytes(StandardCharsets.UTF_8), 1000, new SecureRandom());
        String stored = a.hash("enjoytrip");
        check(!b.verify("enjoytrip", stored), "pepper 가 다르면 실패");
        PasswordHasher stronger = new PasswordHasher("pepper-A".getBytes(StandardCharsets.UTF_8), 2000, new SecureRandom());
        check(stronger.verify("enjoytrip", stored), "저장된 반복 횟수로 검증");
        check(stronger.needsRehash(stored) && !a.needsRehash(stored), "재해시 판단");
        expectIae(() -> a.verify("x", "plain-text-password"));
        expectIae(() -> a.verify("x", "pbkdf2_sha256$abc$AAAA$AAAA"));
        expectIae(() -> new PasswordHasher(new byte[0]));
        // 반복 횟수를 키운 변조 저장값으로 CPU 를 붙잡는 DoS 방지 — 계산 전에 바로 거부해야 한다
        String[] parts = stored.split("\\$");
        long t0 = System.nanoTime();
        expectIae(() -> a.verify("enjoytrip", "pbkdf2_sha256$2000000000$" + parts[2] + "$" + parts[3]));
        check((System.nanoTime() - t0) / 1e6 < 100, "상한 초과는 계산 없이 거부");
        expectIae(() -> a.verify("enjoytrip", "pbkdf2_sha256$1000$AAAA$" + parts[3]));
        expectIae(() -> new PasswordHasher(null, PasswordHasher.MAX_ITERATIONS + 1, new SecureRandom()));
    }

    // ---------------------------------------------------------------- #4 자동완성

    static void trieBasics() {
        AutocompleteTrie<String> trie = new AutocompleteTrie<String>(3);
        trie.add("담양 죽녹원", "1", 5);
        trie.add("담양 메타세쿼이아길", "2", 9);
        trie.add("담빛체험학습장", "3", 1);
        trie.add("한려해상국립공원 (오동도)", "4", 7);
        trie.add("담양호", "5", 3);
        check(trie.suggest("담", 10).equals(Arrays.asList("2", "1", "5")), "점수순 Top-3 " + trie.suggest("담", 10));
        check(trie.suggest("담양 죽", 10).equals(Collections.singletonList("1")), "공백 무시");
        check(trie.suggest("죽녹", 10).equals(Collections.singletonList("1")), "두 번째 단어");
        check(trie.suggest("오동", 10).equals(Collections.singletonList("4")), "괄호 안 단어");
        check(trie.suggest("ㄷㅇ", 10).equals(Arrays.asList("2", "1", "5")), "초성 " + trie.suggest("ㄷㅇ", 10));
        check(trie.suggest("ㅈㄴㅇ", 10).equals(Collections.singletonList("1")), "초성 두 번째 단어");
        check(trie.suggest("부산", 10).isEmpty() && trie.suggest("", 10).isEmpty(), "없음");
        check(trie.suggest("담", 1).equals(Collections.singletonList("2")), "limit");
        check(KoreanText.toChoseong("광주송정역").equals("ㄱㅈㅅㅈㅇ"), "초성 변환");
    }

    static void trieMatchesBruteForce() {
        Random rnd = new Random(8);
        String syll = "가나다라마바사아자차카타파하강남동서산해";
        List<String> names = new ArrayList<String>();
        List<Double> scores = new ArrayList<Double>();
        AutocompleteTrie<Integer> trie = new AutocompleteTrie<Integer>(10);
        for (int i = 0; i < 2000; i++) {
            StringBuilder sb = new StringBuilder();
            int words = 1 + rnd.nextInt(2);
            for (int w = 0; w < words; w++) {
                if (w > 0) sb.append(' ');
                for (int c = 0; c < 2 + rnd.nextInt(3); c++) sb.append(syll.charAt(rnd.nextInt(syll.length())));
            }
            names.add(sb.toString());
            scores.add((double) rnd.nextInt(100));
            trie.add(names.get(i), i, scores.get(i));
        }
        for (int q = 0; q < 500; q++) {
            String base = names.get(rnd.nextInt(names.size()));
            String[] words = base.split(" ");
            String word = words[rnd.nextInt(words.length)];
            String prefix = word.substring(0, 1 + rnd.nextInt(word.length()));
            boolean cho = q % 3 == 0;
            String query = cho ? KoreanText.toChoseong(prefix) : prefix;
            List<Integer> expected = bruteSuggest(names, scores, query, cho, 10);
            List<Integer> actual = trie.suggest(query, 10);
            check(expected.equals(actual), "query=" + query + " expected=" + expected + " actual=" + actual);
        }
    }

    private static List<Integer> bruteSuggest(final List<String> names, final List<Double> scores, String query, boolean cho, int k) {
        List<Integer> hits = new ArrayList<Integer>();
        for (int i = 0; i < names.size(); i++) {
            String[] words = names.get(i).split(" ");
            List<String> keys = new ArrayList<String>();
            keys.add(KoreanText.normalize(names.get(i)));
            for (int w = 1; w < words.length; w++) keys.add(KoreanText.normalize(words[w]));
            for (String key : keys) {
                String target = cho ? KoreanText.toChoseong(key) : key;
                if (target.startsWith(query)) {
                    hits.add(i);
                    break;
                }
            }
        }
        Collections.sort(hits, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int c = Double.compare(scores.get(b), scores.get(a));
                return c != 0 ? c : names.get(a).compareTo(names.get(b));
            }
        });
        return hits.size() > k ? hits.subList(0, k) : hits;
    }

    // ---------------------------------------------------------------- #5 KMP

    static void kmpBasics() {
        check(Arrays.equals(KmpMatcher.failure("ABABCABAB"), new int[]{0, 0, 1, 2, 0, 1, 2, 3, 4}), "실패 함수");
        check(new KmpMatcher("aaa").findAll("aaaaa").equals(Arrays.asList(0, 1, 2)), "겹침");
        check(new KmpMatcher("국립공원").findAll("지리산국립공원과 무등산국립공원").equals(Arrays.asList(3, 12)), "한글");
        check(new KmpMatcher("xyz").findAll("abc").isEmpty(), "없음");
        check(new KmpMatcher("ab").matches("xxab") && !new KmpMatcher("ab").matches("ba"), "matches");
        expectIae(() -> new KmpMatcher(""));
    }

    static void kmpMatchesNaive() {
        Random rnd = new Random(13);
        for (int t = 0; t < 2000; t++) {
            String text = randomString(rnd, rnd.nextInt(60), 2 + t % 3);
            String pat = randomString(rnd, 1 + rnd.nextInt(5), 2 + t % 3);
            List<Integer> expected = new ArrayList<Integer>();
            for (int i = 0; i + pat.length() <= text.length(); i++) {
                if (text.regionMatches(i, pat, 0, pat.length())) expected.add(i);
            }
            check(new KmpMatcher(pat).findAll(text).equals(expected), "text=" + text + " pat=" + pat);
            check(new KmpMatcher(pat).matches(text) == !expected.isEmpty(), "matches " + text + " " + pat);
        }
    }

    static void keywordSearcher() {
        List<String[]> spots = Arrays.asList(
                new String[]{"무등산 주상절리대", "광주 북구 금곡동"},
                new String[]{"광주호 호수생태원", "광주 북구 충효동"},
                new String[]{"Gwangju Art Street", "광주 동구 예술길"});
        KeywordSearcher<String[]> searcher = new KeywordSearcher<String[]>(new KeywordSearcher.Fields<String[]>() {
            @Override
            public String[] of(String[] item) {
                return item;
            }
        });
        List<KeywordSearcher.Match<String[]>> r = searcher.search(spots, "광주", 10);
        check(r.size() == 3 && r.get(0).getItem()[0].equals("광주호 호수생태원") && r.get(0).getFieldIndex() == 0, "이름 우선");
        check(r.get(0).getPositions().equals(Collections.singletonList(0)), "위치");
        List<KeywordSearcher.Match<String[]>> en = searcher.search(spots, "art", 10);
        check(en.size() == 1 && en.get(0).getPositions().equals(Collections.singletonList(8)), "대소문자 무시");
        check(searcher.search(spots, "  ", 10).isEmpty(), "빈 검색어");
    }

    // ---------------------------------------------------------------- #6 TimSort

    static void timSortMatchesJdk() {
        Random rnd = new Random(21);
        Comparator<int[]> byKey = new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(a[0], b[0]);
            }
        };
        int[] sizes = {0, 1, 2, 3, 31, 32, 33, 64, 65, 100, 257, 1000, 5000, 40000};
        for (int n : sizes) {
            for (int pattern = 0; pattern < 6; pattern++) {
                int[][] a = new int[n][];
                for (int i = 0; i < n; i++) {
                    int key;
                    switch (pattern) {
                        case 0: key = rnd.nextInt(); break;                       // 무작위
                        case 1: key = i; break;                                   // 정렬됨
                        case 2: key = n - i; break;                               // 역순
                        case 3: key = i % 50; break;                              // 톱니 (짧은 run 반복)
                        case 4: key = rnd.nextInt(5); break;                      // 중복 많음 (안정성 확인)
                        default: key = i < n / 2 ? i : rnd.nextInt(n + 1);        // 앞쪽만 정렬
                    }
                    a[i] = new int[]{key, i};
                }
                int[][] expected = a.clone();
                Arrays.sort(expected, byKey); // JDK 객체 정렬도 안정 정렬(TimSort)
                int[][] actual = a.clone();
                TimSort.sort(actual, byKey);
                for (int i = 0; i < n; i++) {
                    check(actual[i][0] == expected[i][0] && actual[i][1] == expected[i][1],
                            "n=" + n + " pattern=" + pattern + " i=" + i);
                }
            }
        }
        List<String> list = new ArrayList<String>(Arrays.asList("순천만", "담양", "여수", "광주", "목포"));
        TimSort.sort(list, Comparator.<String>naturalOrder());
        check(list.equals(Arrays.asList("광주", "담양", "목포", "순천만", "여수")), "List 정렬");
    }

    static void timSortSortedIsLinear() {
        final int[] count = {0};
        Integer[] a = new Integer[10000];
        for (int i = 0; i < a.length; i++) a[i] = i;
        TimSort.sort(a, new Comparator<Integer>() {
            @Override
            public int compare(Integer x, Integer y) {
                count[0]++;
                return x.compareTo(y);
            }
        });
        check(count[0] == a.length - 1, "비교 횟수 " + count[0]);
    }

    // ---------------------------------------------------------------- #7 추천 (격자 인덱스 포함)

    static void gridMatchesBruteForce() {
        Random rnd = new Random(34);
        List<Location> pts = AlgorithmTest.randomPoints(rnd, 3000);
        GridIndex<Location> grid = new GridIndex<Location>(pts, 2.0, 35);
        for (int t = 0; t < 200; t++) {
            Location c = AlgorithmTest.randomPoint(rnd, "c");
            double radius = rnd.nextDouble() * 15;
            int expectedCount = 0;
            for (Location p : pts) {
                if (HaversineDistance.greatCircleKm(c.getLat(), c.getLng(), p.getLat(), p.getLng()) <= radius) expectedCount++;
            }
            check(grid.countWithin(c.getLat(), c.getLng(), radius) == expectedCount, "반경 t=" + t);

            int k = 1 + rnd.nextInt(20);
            List<Location> near = grid.nearest(c.getLat(), c.getLng(), k);
            List<Double> expected = new ArrayList<Double>();
            for (Location p : pts) expected.add(HaversineDistance.greatCircleKm(c.getLat(), c.getLng(), p.getLat(), p.getLng()));
            Collections.sort(expected);
            check(near.size() == k, "개수");
            for (int i = 0; i < k; i++) {
                double d = HaversineDistance.greatCircleKm(c.getLat(), c.getLng(), near.get(i).getLat(), near.get(i).getLng());
                check(Math.abs(d - expected.get(i)) < 1e-9, "최근접 t=" + t + " i=" + i);
            }
        }
    }

    private static final SpotRecommender.Features<Spot> SPOT_FEATURES = new SpotRecommender.Features<Spot>() {
        @Override
        public Collection<String> of(Spot item) {
            return item.codes;
        }
    };

    static void recommenderBasics() {
        Random rnd = new Random(55);
        String[][] cats = {{"NA", "NA02"}, {"NA", "NA04"}, {"HS", "HS01"}, {"HS", "HS03"}, {"EX", "EX03"}, {"VE", "VE01"}};
        List<Spot> spots = new ArrayList<Spot>();
        for (int i = 0; i < 300; i++) {
            Location p = AlgorithmTest.randomPoint(rnd, "s" + i);
            spots.add(new Spot("s" + i, p.getLat(), p.getLng(), cats[i % cats.length]));
        }
        SpotRecommender<Spot> rec = new SpotRecommender<Spot>(spots, SPOT_FEATURES, null, SpotRecommender.Weights.defaults());
        // 역사(HS01) 관광지 3곳을 찜한 사용자
        List<String> liked = Arrays.asList("s2", "s8", "s14");
        List<SpotRecommender.Recommendation<Spot>> top = rec.recommend(new SpotRecommender.Preference(liked, null, null, null), 10);
        check(top.size() == 10, "개수");
        for (SpotRecommender.Recommendation<Spot> r : top) {
            check(r.getItem().codes.contains("HS01"), "취향 반영 " + r.getItem().codes);
            check(!liked.contains(r.getItem().getId()), "찜한 곳 제외");
            check(Math.abs(r.getTasteScore() - 1.0) < 1e-9, "같은 분류는 코사인 1");
        }
        // 직접 고른 선호 분류
        Map<String, Double> prefer = new HashMap<String, Double>();
        prefer.put("EX", 1.0);
        prefer.put("EX03", 1.0);
        List<SpotRecommender.Recommendation<Spot>> ex = rec.recommend(new SpotRecommender.Preference(null, prefer, null, null), 5);
        for (SpotRecommender.Recommendation<Spot> r : ex) check(r.getItem().codes.contains("EX03"), "선호 분류");

        // Top-K 힙 결과 = 전체 정렬 상위 K (기준 위치 포함)
        Location base = new Location("b", "b", 35.15, 126.85);
        SpotRecommender.Preference p = new SpotRecommender.Preference(liked, prefer, base, Collections.singletonList("s0"));
        List<SpotRecommender.Recommendation<Spot>> all = rec.recommend(p, 1000);
        List<SpotRecommender.Recommendation<Spot>> k20 = rec.recommend(p, 20);
        check(all.size() == 300 - 4, "전체 개수 " + all.size());
        for (int i = 0; i < 20; i++) check(all.get(i).getItem() == k20.get(i).getItem(), "Top-K i=" + i);
        for (int i = 1; i < all.size(); i++) check(all.get(i - 1).getScore() >= all.get(i).getScore(), "내림차순");
    }

    static void recommenderChargerOnly() {
        List<Spot> spots = Arrays.asList(
                new Spot("a", 35.00, 126.50, new String[]{"NA"}),
                new Spot("b", 35.20, 126.90, new String[]{"NA"}),
                new Spot("c", 34.80, 127.50, new String[]{"NA"}));
        List<Location> chargers = new ArrayList<Location>();
        for (int i = 0; i < 4; i++) chargers.add(new Location("cb" + i, "cb", 35.20 + i * 0.001, 126.90));
        chargers.add(new Location("ca", "ca", 35.00, 126.501));
        GridIndex<Location> grid = new GridIndex<Location>(chargers, 1.0, 35);
        SpotRecommender<Spot> rec = new SpotRecommender<Spot>(spots, SPOT_FEATURES, grid,
                new SpotRecommender.Weights(0, 0, 1, 30, 3, 5));
        List<SpotRecommender.Recommendation<Spot>> r = rec.recommend(new SpotRecommender.Preference(null, null, null, null), 3);
        check(r.get(0).getItem().getId().equals("b") && r.get(0).getFastChargersNearby() == 4, "b 4곳");
        check(r.get(1).getItem().getId().equals("a") && r.get(2).getFastChargersNearby() == 0, "a 1곳, c 0곳");
        expectIae(() -> new SpotRecommender.Weights(0, 0, 0, 30, 3, 5));
        // 거리 가중치만 있고 기준 위치가 없으면 점수가 NaN 이 되지 않고 입력 오류로 거부
        final SpotRecommender<Spot> distanceOnly = rec.withWeights(new SpotRecommender.Weights(0, 1, 0, 30, 3, 5));
        expectIae(() -> distanceOnly.recommend(new SpotRecommender.Preference(null, null, null, null), 3));
        double s = distanceOnly.recommend(new SpotRecommender.Preference(null, null, new Location("b", "b", 35.2, 126.9), null), 1).get(0).getScore();
        check(!Double.isNaN(s) && s > 0.99, "기준 위치가 있으면 정상 " + s);
    }

    // ---------------------------------------------------------------- #8 도로 거리

    static void kakaoParse() {
        String ok = "{\"trans_id\":\"x\",\"routes\":[{\"result_code\":0,\"result_msg\":\"길찾기 성공\",\"summary\":{\"origin\":{\"x\":1,\"y\":2},"
                + "\"fare\":{\"taxi\":1,\"toll\":0},\"distance\":34567,\"duration\":2100},\"sections\":[{\"distance\":34567}]}]}";
        check(Math.abs(KakaoRoadDistanceAccess.parse(ok) - 34.567) < 1e-9, "distance");
        String fail = "{\"routes\":[{\"result_code\":104,\"result_msg\":\"출발지와 도착지가 5 m 이내로 설정된 경우 경로를 탐색할 수 없음\"}]}";
        boolean thrown = false;
        try {
            KakaoRoadDistanceAccess.parse(fail);
        } catch (IllegalStateException e) {
            thrown = e.getMessage().contains("104");
        }
        check(thrown, "실패 코드");
        expectIae(() -> new KakaoRoadDistance(" "));
    }

    // ---------------------------------------------------------------- #9 오타 허용 검색

    static void hangulRoundTrip() {
        for (char c = '가'; c <= '힣'; c++) {
            String s = String.valueOf(c);
            check(Hangul.compose(new String(Hangul.toKeys(s).keys)).equals(s), "글자 " + s);
        }
        check(new String(Hangul.toKeys("뷁").keys).equals("ㅂㅜㅔㄹㄱ"), "겹모음·겹받침은 키 2개씩");
        check(new String(Hangul.toKeys("무등산 Park!").keys).equals("ㅁㅜㄷㅡㅇㅅㅏㄴpark"), "공백·기호 제외, 영문 소문자");
        Random rnd = new Random(91);
        for (int t = 0; t < 2000; t++) {
            String s = randomHangul(rnd, 1 + rnd.nextInt(6));
            check(Hangul.compose(new String(Hangul.toKeys(s).keys)).equals(s), "문자열 " + s);
        }
    }

    static void qwertyConversion() {
        check("담양".equals(Hangul.fromQwerty("ekadid")), "담양 " + Hangul.fromQwerty("ekadid"));
        check("국립공원".equals(Hangul.fromQwerty("rnrflqrhddnjs")), "국립공원");
        check("안녕하세요".equals(Hangul.fromQwerty("dkssudgktpdy")), "안녕하세요");
        check("뷁".equals(Hangul.fromQwerty("qnpfr")), "뷁");
        check(Hangul.fromQwerty("123") == null && Hangul.fromQwerty("담양") == null, "변환 대상 아님");
        Random rnd = new Random(92);
        for (int t = 0; t < 2000; t++) {
            String s = randomHangul(rnd, 1 + rnd.nextInt(6));
            check(s.equals(Hangul.fromQwerty(Hangul.toQwerty(s))), "왕복 " + s + " " + Hangul.toQwerty(s));
        }
    }

    static void editDistanceMatchesBruteForce() {
        check(EditDistance.distance("kitten".toCharArray(), "sitting".toCharArray()) == 3, "kitten");
        check(EditDistance.distance("ab".toCharArray(), "ba".toCharArray()) == 1, "전치");
        check(EditDistance.distance("".toCharArray(), "abc".toCharArray()) == 3, "빈 문자열");
        Random rnd = new Random(93);
        for (int t = 0; t < 3000; t++) {
            char[] p = randomString(rnd, 1 + rnd.nextInt(6), 3).toCharArray();
            char[] text = randomString(rnd, rnd.nextInt(12), 3).toCharArray();
            int brute = Integer.MAX_VALUE;
            for (int i = 0; i <= text.length; i++) {
                for (int j = i; j <= text.length; j++) {
                    brute = Math.min(brute, EditDistance.distance(p, Arrays.copyOfRange(text, i, j)));
                }
            }
            int max = rnd.nextInt(4);
            EditDistance.Match m = EditDistance.bestSubstring(p, text, max);
            if (brute > max) {
                check(m == null, "t=" + t + " 허용 거리 초과는 null");
            } else {
                check(m != null && m.distance == brute, "t=" + t + " brute=" + brute + " got=" + (m == null ? "null" : m.distance));
                check(EditDistance.distance(p, Arrays.copyOfRange(text, m.start, m.end)) == brute, "t=" + t + " 구간");
            }
        }
    }

    static void fuzzySearchFilterIsExact() {
        Random rnd = new Random(94);
        List<String[]> rows = new ArrayList<String[]>();
        for (int i = 0; i < 800; i++) {
            rows.add(new String[]{randomHangul(rnd, 2 + rnd.nextInt(6)) + " " + randomHangul(rnd, 2 + rnd.nextInt(3)),
                    randomHangul(rnd, 3) + " " + randomHangul(rnd, 3) + " " + (1 + rnd.nextInt(300))});
        }
        KeywordSearcher.Fields<String[]> identity = new KeywordSearcher.Fields<String[]>() {
            @Override
            public String[] of(String[] item) {
                return item;
            }
        };
        FuzzySearcher<String[]> searcher = new FuzzySearcher<String[]>(rows, identity);
        for (int t = 0; t < 500; t++) {
            String name = rows.get(rnd.nextInt(rows.size()))[0].replace(" ", "");
            int from = rnd.nextInt(Math.max(1, name.length() - 2));
            String query = typo(rnd, name.substring(from, Math.min(name.length(), from + 2 + rnd.nextInt(4))));
            Hangul.Keys q = Hangul.toKeys(query);
            int k = FuzzySearcher.defaultMaxDistance(q.length());
            FuzzySearcher.Result<String[]> indexed = searcher.search(q, k, 1000, true);
            FuzzySearcher.Result<String[]> all = searcher.search(q, k, 1000, false);
            check(indexed.getMatches().size() == all.getMatches().size(),
                    "t=" + t + " " + query + " 개수 " + indexed.getMatches().size() + " vs " + all.getMatches().size());
            for (int i = 0; i < all.getMatches().size(); i++) {
                check(indexed.getMatches().get(i).getItem() == all.getMatches().get(i).getItem()
                        && indexed.getMatches().get(i).getDistance() == all.getMatches().get(i).getDistance(), "t=" + t + " 순위 " + i);
            }
            check(indexed.getCandidates() <= all.getCandidates(), "후보 수");
        }
        // 실제 예시: 받침 하나 틀린 "국림공원", 모음 하나 틀린 "무둥산"
        List<String[]> spots = Arrays.asList(new String[]{"무등산국립공원(광주)", "광주광역시 북구"},
                new String[]{"월출산국립공원", "영암군"}, new String[]{"국립아시아문화전당", "광주 동구"});
        FuzzySearcher<String[]> real = new FuzzySearcher<String[]>(spots, identity);
        List<FuzzySearcher.Match<String[]>> r = real.search("국림공원", 10).getMatches();
        check(r.size() == 2 && r.get(0).getDistance() == 1, "국림공원 " + r.size());
        check("국립공원".equals(r.get(0).getItem()[0].substring(r.get(0).getStart(), r.get(0).getEnd())), "하이라이트 구간");
        List<FuzzySearcher.Match<String[]>> m = real.search("무둥산", 10).getMatches();
        check(!m.isEmpty() && m.get(0).getItem() == spots.get(0) && m.get(0).getDistance() == 1, "무둥산");
        check(real.search("ㅋ", 10).getMatches().isEmpty(), "짧은 검색어는 정확히 맞아야 함");
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 40; i++) tooLong.append('뷁');
        expectIae(() -> real.search(tooLong.toString(), 10));
    }

    /** 한글 문자열의 자모 키 하나를 바꾸거나 지우거나 이웃과 맞바꾼다 */
    private static String typo(Random rnd, String s) {
        char[] k = Hangul.toKeys(s).keys;
        if (k.length < 2) return s;
        StringBuilder sb = new StringBuilder(new String(k));
        int p = rnd.nextInt(k.length - 1);
        switch (rnd.nextInt(3)) {
            case 0:
                sb.setCharAt(p, "ㄱㄴㄷㄹㅁㅂㅅㅇㅏㅓㅗㅜㅡㅣ".charAt(rnd.nextInt(14)));
                break;
            case 1:
                sb.deleteCharAt(p);
                break;
            default:
                char c = sb.charAt(p);
                sb.setCharAt(p, sb.charAt(p + 1));
                sb.setCharAt(p + 1, c);
        }
        return Hangul.compose(sb);
    }

    private static String randomHangul(Random rnd, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append((char) ('가' + rnd.nextInt(11172)));
        return sb.toString();
    }

    // ---------------------------------------------------------------- helpers

    static final class Spot extends Location {
        final List<String> codes;

        Spot(String id, double lat, double lng, String[] codes) {
            super(id, id, lat, lng);
            this.codes = Arrays.asList(codes);
        }
    }

    private static String randomString(Random rnd, int len, int alphabet) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append((char) ('a' + rnd.nextInt(alphabet)));
        return sb.toString();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
