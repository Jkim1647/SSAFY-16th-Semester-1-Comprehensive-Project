package com.ssafy.enjoytrip.model.service;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlanner;
import com.ssafy.enjoytrip.algorithm.geo.GridIndex;
import com.ssafy.enjoytrip.algorithm.geo.HaversineDistance;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.recommend.SpotRecommender;
import com.ssafy.enjoytrip.algorithm.search.AutocompleteTrie;
import com.ssafy.enjoytrip.algorithm.search.FuzzySearcher;
import com.ssafy.enjoytrip.algorithm.search.Hangul;
import com.ssafy.enjoytrip.algorithm.search.KeywordSearcher;
import com.ssafy.enjoytrip.algorithm.sort.TimSort;
import com.ssafy.enjoytrip.model.dao.AttractionDao;
import com.ssafy.enjoytrip.model.dao.CategoryCodeDao;
import com.ssafy.enjoytrip.model.dao.ChargingStationDao;
import com.ssafy.enjoytrip.model.dto.AttractionDto;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.Autocomplete;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.Category;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.Recommend;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.Recommendation;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.Search;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.SearchHit;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.SortedList;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.SortedSpot;
import com.ssafy.enjoytrip.model.dto.AttractionResponses.Spot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관광지 검색(#4 Trie, #5 KMP, #9 오타 허용), 정렬(#6 TimSort), 추천(#7 코사인 + 격자 인덱스 + Top-K).
 * 자료구조는 생성할 때 한 번 만들고 이후에는 읽기만 하므로 여러 요청이 동시에 써도 안전하다.
 * 데이터가 바뀌는 구조(DB)로 가면 새로 만든 인스턴스로 참조를 바꿔 끼우는 방식으로 갱신한다.
 */
@Service
public class AttractionService {

    /** 반경 3km 안 급속 충전소 수 (추천 점수와 자동완성 순서에 사용) */
    public static final double CHARGER_RADIUS_KM = 3;
    /** 검색어 최대 글자 수. 검색 단계(KMP·오타 허용 DP)의 비용이 검색어 길이에 비례하므로 입력에서 막는다. */
    public static final int MAX_QUERY_LENGTH = 30;

    private final List<AttractionDto> attractions;
    private final Map<String, String> codeNames;
    private final AutocompleteTrie<AttractionDto> trie = new AutocompleteTrie<>(10);
    private final KeywordSearcher<AttractionDto> searcher;
    private final FuzzySearcher<AttractionDto> fuzzy;
    private final SpotRecommender<AttractionDto> recommender;

    public AttractionService(AttractionDao attractionDao, ChargingStationDao stationDao, CategoryCodeDao codeDao) {
        this.attractions = attractionDao.findAll();
        this.codeNames = codeDao.findAll();

        List<ChargingStation> fast = new ArrayList<>();
        for (ChargingStation s : stationDao.findAll()) {
            if (s.getMaxKw() >= EvRoutePlanner.DEFAULT_MIN_CHARGER_KW) fast.add(s);
        }
        GridIndex<ChargingStation> grid = new GridIndex<>(fast, 2.0, 35);
        this.recommender = new SpotRecommender<>(attractions, AttractionDto::categoryCodes, grid, defaultWeights());
        for (AttractionDto a : attractions) {
            // EV 여행자에게 충전하기 좋은 곳을 먼저 보여 주도록 자동완성 점수 = 주변 급속 충전소 수
            trie.add(a.getName(), a, recommender.fastChargersNearby(a.getId()));
        }
        KeywordSearcher.Fields<AttractionDto> fields = item -> new String[]{item.getName(), item.getAddr()};
        this.searcher = new KeywordSearcher<>(fields);
        this.fuzzy = new FuzzySearcher<>(attractions, fields);
    }

    static SpotRecommender.Weights defaultWeights() {
        return new SpotRecommender.Weights(0.6, 0.25, 0.15, 30, CHARGER_RADIUS_KM, 5);
    }

    /** 접두어 자동완성. 결과가 없고 한/영 전환 오타로 보이면 한글로 바꿔 다시 찾는다 (ekadid → 담양). */
    public Autocomplete autocomplete(String query, int limit) {
        checkLength(query);
        long t0 = System.nanoTime();
        List<AttractionDto> found = trie.suggest(query, limit);
        String corrected = null;
        if (found.isEmpty()) {
            String converted = Hangul.fromQwerty(query);
            if (converted != null) {
                found = trie.suggest(converted, limit);
                if (!found.isEmpty()) corrected = converted;
            }
        }
        double us = (System.nanoTime() - t0) / 1e3;
        return new Autocomplete(query, corrected, us, found.stream().map(this::spot).toList());
    }

    /**
     * 키워드 검색. 1) KMP 정확 일치 → 2) 없으면 한/영 전환 오타를 고쳐 정확 일치 → 3) 그래도 없으면 오타 허용 검색(#9).
     * 오타 허용 검색은 가장 가까운 거리의 결과만 돌려준다 (더 먼 결과까지 섞으면 엉뚱한 곳이 끼기 쉽다).
     */
    public Search search(String keyword, int limit) {
        String query = keyword == null ? "" : keyword.trim();
        checkLength(query);
        long t0 = System.nanoTime();
        List<SearchHit> hits = exact(query, limit);
        String mode = "exact";
        String corrected = null;
        String suggestion = null;
        if (hits.isEmpty() && !query.isEmpty()) {
            String converted = Hangul.fromQwerty(query);
            if (converted != null) {
                hits = exact(converted, limit);
                if (!hits.isEmpty()) {
                    mode = "keyboard";
                    corrected = converted;
                }
            }
            if (hits.isEmpty()) {
                String fuzzyQuery = converted != null ? converted : query;
                List<FuzzySearcher.Match<AttractionDto>> matches = fuzzy.search(fuzzyQuery, limit).getMatches();
                if (!matches.isEmpty()) {
                    int best = matches.get(0).getDistance();
                    hits = matches.stream().filter(m -> m.getDistance() == best).map(this::fuzzyHit).toList();
                    mode = "fuzzy";
                    corrected = converted;
                    FuzzySearcher.Match<AttractionDto> top = matches.get(0);
                    String text = top.getFieldIndex() == 0 ? top.getItem().getName() : top.getItem().getAddr();
                    suggestion = text.substring(top.getStart(), top.getEnd());
                }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        return new Search(query, mode, corrected, suggestion, ms, hits);
    }

    private static void checkLength(String query) {
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("검색어는 " + MAX_QUERY_LENGTH + "자 이하로 입력하세요.");
        }
    }

    private List<SearchHit> exact(String query, int limit) {
        return searcher.search(attractions, query, limit).stream()
                .map(m -> new SearchHit(spot(m.getItem()), m.getFieldIndex() == 0 ? "name" : "addr", m.getKeywordLength(), m.getPositions(), 0))
                .toList();
    }

    private SearchHit fuzzyHit(FuzzySearcher.Match<AttractionDto> m) {
        return new SearchHit(spot(m.getItem()), m.getFieldIndex() == 0 ? "name" : "addr", m.getEnd() - m.getStart(),
                List.of(m.getStart()), m.getDistance());
    }

    /**
     * 정렬 기준(distance / chargers / name)과 분류 필터. 이름순으로 먼저 정렬한 뒤 기준으로 다시 안정 정렬해
     * 거리·충전소 수가 같은 관광지는 이름순이 유지된다.
     */
    public SortedList sorted(String by, String category, Location base, int limit) {
        Comparator<AttractionDto> primary = switch (by) {
            case "distance" -> Comparator.comparingDouble(a -> km(base, a));
            case "chargers" -> (a, b) -> Integer.compare(recommender.fastChargersNearby(b.getId()), recommender.fastChargersNearby(a.getId()));
            case "name" -> null;
            default -> throw new IllegalArgumentException("정렬 기준은 distance, chargers, name 중 하나입니다.");
        };
        List<AttractionDto> list = new ArrayList<>();
        for (AttractionDto a : attractions) {
            if (category == null || category.isEmpty() || a.getLcls1().equals(category) || a.getLcls2().equals(category)) list.add(a);
        }
        long t0 = System.nanoTime();
        TimSort.sort(list, Comparator.comparing(AttractionDto::getName));
        if (primary != null) TimSort.sort(list, primary);
        double ms = (System.nanoTime() - t0) / 1e6;
        List<SortedSpot> items = list.subList(0, Math.min(limit, list.size())).stream()
                .map(a -> new SortedSpot(spot(a), km(base, a))).toList();
        return new SortedList(by, list.size(), ms, items);
    }

    /**
     * @param base 기준 위치. null 이면 거리 점수를 쓰지 않는다 (이때 거리 가중치만 주면 IllegalArgumentException)
     */
    public Recommend recommend(Collection<String> liked, Collection<String> preferredCodes, Location base,
                               double wTaste, double wDistance, double wCharger, int k) {
        Map<String, Double> prefer = new HashMap<>();
        for (String code : preferredCodes) prefer.put(code, 1.0);
        SpotRecommender<AttractionDto> rec = recommender.withWeights(
                new SpotRecommender.Weights(wTaste, wDistance, wCharger, 30, CHARGER_RADIUS_KM, 5));
        long t0 = System.nanoTime();
        List<SpotRecommender.Recommendation<AttractionDto>> list = rec.recommend(new SpotRecommender.Preference(liked, prefer, base, null), k);
        double ms = (System.nanoTime() - t0) / 1e6;
        List<Recommendation> items = list.stream().map(r -> new Recommendation(spot(r.getItem()), r.getScore(), r.getTasteScore(),
                r.getDistanceScore(), r.getChargerScore(), r.getFastChargersNearby(), base == null ? null : km(base, r.getItem()))).toList();
        return new Recommend(ms, items);
    }

    /** 분류별 관광지 수 (대분류 level 1, 중분류 level 2) */
    public List<Category> categories() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AttractionDto a : attractions) {
            for (String code : new String[]{a.getLcls1(), a.getLcls2()}) {
                if (!code.isEmpty()) counts.merge(code, 1, Integer::sum);
            }
        }
        List<Category> list = new ArrayList<>();
        counts.forEach((code, count) -> list.add(new Category(code, codeNames.get(code), count, code.length() == 2 ? 1 : 2)));
        return list;
    }

    public int count() {
        return attractions.size();
    }

    Spot spot(AttractionDto a) {
        return new Spot(a.getId(), a.getName(), a.getAddr(), a.getLat(), a.getLng(),
                codeNames.get(a.getLcls1()), codeNames.get(a.getLcls2()), recommender.fastChargersNearby(a.getId()));
    }

    private static double km(Location base, Location a) {
        return HaversineDistance.greatCircleKm(base.getLat(), base.getLng(), a.getLat(), a.getLng());
    }
}
