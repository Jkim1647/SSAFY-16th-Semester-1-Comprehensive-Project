package com.ssafy.enjoytrip.algorithm.recommend;

import com.ssafy.enjoytrip.algorithm.geo.GridIndex;
import com.ssafy.enjoytrip.algorithm.geo.HaversineDistance;
import com.ssafy.enjoytrip.algorithm.geo.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 취향·거리·충전 편의를 섞은 관광지 추천.
 *
 * <ol>
 *   <li><b>취향 (코사인 유사도)</b>: 관광지를 분류 코드(대·중·소분류) 특징 벡터로 표현한다.
 *       사용자 벡터 = 찜·방문한 관광지 벡터의 합 + 직접 고른 선호 분류의 가중치.
 *       cos θ = (u·v) / (|u||v|) 로 방향(취향)이 얼마나 같은지만 본다 — 많이 찜한 사람과 적게 찜한 사람을 공평하게 비교.</li>
 *   <li><b>거리</b>: 기준 위치(숙소·현재 위치)에서 가까울수록 높게, exp(-거리/scaleKm).</li>
 *   <li><b>충전 편의</b>: 관광지 반경 안 급속 충전소 수 (격자 인덱스로 미리 계산), saturation 개 이상이면 만점.</li>
 * </ol>
 * 최종 점수 = 세 점수의 가중 평균. 사용자가 무엇을 중요하게 여기는지에 따라 {@link Weights} 를 바꾼다.
 * 상위 K 개는 크기 K 의 최소 힙으로 고른다 — 전체 정렬 O(N log N) 대신 O(N log K).
 */
public class SpotRecommender<T extends Location> {

    public interface Features<T> {
        /** 항목의 특징 (예: 분류 코드 "NA", "NA02", "NA020500") */
        Collection<String> of(T item);
    }

    public static class Weights {
        final double taste;
        final double distance;
        final double charger;
        final double distanceScaleKm;
        final double chargerRadiusKm;
        final int chargerSaturation;

        public Weights(double taste, double distance, double charger,
                       double distanceScaleKm, double chargerRadiusKm, int chargerSaturation) {
            if (taste < 0 || distance < 0 || charger < 0 || taste + distance + charger <= 0) {
                throw new IllegalArgumentException("가중치는 0 이상이고 합이 0보다 커야 합니다.");
            }
            if (!(distanceScaleKm > 0) || !(chargerRadiusKm > 0) || chargerSaturation < 1) {
                throw new IllegalArgumentException("거리 척도·충전소 반경·포화 개수가 올바르지 않습니다.");
            }
            this.taste = taste;
            this.distance = distance;
            this.charger = charger;
            this.distanceScaleKm = distanceScaleKm;
            this.chargerRadiusKm = chargerRadiusKm;
            this.chargerSaturation = chargerSaturation;
        }

        /** 취향 0.6, 거리 0.25, 충전 0.15 / 거리 척도 30km, 충전소 반경 3km, 5곳이면 만점 */
        public static Weights defaults() {
            return new Weights(0.6, 0.25, 0.15, 30, 3, 5);
        }
    }

    public static class Preference {
        final Collection<String> likedIds;
        final Map<String, Double> categoryWeights;
        final Location base;
        final Collection<String> excludeIds;

        /**
         * @param likedIds        찜·방문한 관광지 id (추천 결과에서는 빠진다)
         * @param categoryWeights 직접 고른 선호 분류와 가중치 (예: "HS" → 1.0)
         * @param base            기준 위치. null 이면 거리 점수를 쓰지 않는다
         * @param excludeIds      추가로 뺄 관광지 id (이미 계획에 담은 곳 등)
         */
        public Preference(Collection<String> likedIds, Map<String, Double> categoryWeights, Location base,
                          Collection<String> excludeIds) {
            this.likedIds = likedIds == null ? Collections.<String>emptyList() : likedIds;
            this.categoryWeights = categoryWeights == null ? Collections.<String, Double>emptyMap() : categoryWeights;
            this.base = base;
            this.excludeIds = excludeIds == null ? Collections.<String>emptyList() : excludeIds;
        }
    }

    public static class Recommendation<T> {
        private final T item;
        private final double score;
        private final double tasteScore;
        private final double distanceScore;
        private final double chargerScore;
        private final int fastChargersNearby;

        Recommendation(T item, double score, double tasteScore, double distanceScore, double chargerScore, int fastChargersNearby) {
            this.item = item;
            this.score = score;
            this.tasteScore = tasteScore;
            this.distanceScore = distanceScore;
            this.chargerScore = chargerScore;
            this.fastChargersNearby = fastChargersNearby;
        }

        public T getItem() {
            return item;
        }

        public double getScore() {
            return score;
        }

        /** 추천 이유를 화면에 보여줄 때 쓰는 세부 점수 (각 0~1) */
        public double getTasteScore() {
            return tasteScore;
        }

        public double getDistanceScore() {
            return distanceScore;
        }

        public double getChargerScore() {
            return chargerScore;
        }

        public int getFastChargersNearby() {
            return fastChargersNearby;
        }
    }

    private final List<T> items;
    private final Map<String, Integer> indexById = new HashMap<String, Integer>();
    private final List<Map<String, Double>> vectors = new ArrayList<Map<String, Double>>();
    private final int[] chargerCounts;
    private final Weights weights;

    /**
     * @param chargers 급속 충전소 격자 인덱스. null 이면 충전 편의 점수는 0
     */
    public SpotRecommender(List<? extends T> items, Features<? super T> features,
                           GridIndex<? extends Location> chargers, Weights weights) {
        if (items == null || features == null || weights == null) throw new IllegalArgumentException("items, features, weights 가 필요합니다.");
        this.items = new ArrayList<T>(items);
        this.weights = weights;
        this.chargerCounts = new int[this.items.size()];
        for (int i = 0; i < this.items.size(); i++) {
            T item = this.items.get(i);
            indexById.put(item.getId(), i);
            vectors.add(unitVector(features.of(item)));
            chargerCounts[i] = chargers == null ? 0 : chargers.countWithin(item.getLat(), item.getLng(), weights.chargerRadiusKm);
        }
    }

    private SpotRecommender(SpotRecommender<T> source, Weights weights) {
        this.items = source.items;
        this.indexById.putAll(source.indexById);
        this.vectors.addAll(source.vectors);
        this.chargerCounts = source.chargerCounts;
        this.weights = weights;
    }

    /**
     * 가중치만 바꾼 추천기. 특징 벡터와 충전소 수는 다시 계산하지 않고 공유한다 (요청마다 사용자가 가중치를 바꾸는 경우).
     * 충전소 반경이 다르면 미리 센 값을 쓸 수 없으므로 예외.
     */
    public SpotRecommender<T> withWeights(Weights newWeights) {
        if (newWeights == null) throw new IllegalArgumentException("weights 가 null 입니다.");
        if (newWeights.chargerRadiusKm != weights.chargerRadiusKm) {
            throw new IllegalArgumentException("충전소 반경이 다르면 새 추천기를 만들어야 합니다.");
        }
        return new SpotRecommender<T>(this, newWeights);
    }

    public List<Recommendation<T>> recommend(Preference pref, int k) {
        if (pref == null) throw new IllegalArgumentException("preference 가 null 입니다.");
        if (k < 1) return Collections.emptyList();

        Map<String, Double> profile = new HashMap<String, Double>();
        Set<String> exclude = new HashSet<String>(pref.excludeIds);
        for (String id : pref.likedIds) {
            exclude.add(id);
            int idx = indexOf(id);
            if (idx >= 0) addInto(profile, vectors.get(idx), 1.0);
        }
        addInto(profile, pref.categoryWeights, 1.0);
        double profileNorm = norm(profile);

        double weightSum = weights.taste + (pref.base == null ? 0 : weights.distance) + weights.charger;
        // 기준 위치가 없으면 거리 점수를 뺀다. 그때 남는 가중치가 0 이면 점수가 0/0 = NaN 이 되므로 입력 오류로 알린다.
        if (weightSum <= 0) throw new IllegalArgumentException("거리 가중치만 쓰려면 기준 위치가 필요합니다.");
        PriorityQueue<Recommendation<T>> heap = new PriorityQueue<Recommendation<T>>(k, byScore);
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            if (exclude.contains(item.getId())) continue;
            double taste = profileNorm == 0 ? 0 : dot(profile, vectors.get(i)) / profileNorm; // 항목 벡터는 이미 단위 벡터
            double dist = 0;
            if (pref.base != null) {
                double km = HaversineDistance.greatCircleKm(pref.base.getLat(), pref.base.getLng(), item.getLat(), item.getLng());
                dist = Math.exp(-km / weights.distanceScaleKm);
            }
            double charger = Math.min(1.0, chargerCounts[i] / (double) weights.chargerSaturation);
            double score = (weights.taste * taste + (pref.base == null ? 0 : weights.distance * dist) + weights.charger * charger) / weightSum;
            Recommendation<T> rec = new Recommendation<T>(item, score, taste, dist, charger, chargerCounts[i]);
            if (heap.size() < k) heap.add(rec);
            else if (byScore.compare(rec, heap.peek()) > 0) {
                heap.poll();
                heap.add(rec);
            }
        }
        List<Recommendation<T>> result = new ArrayList<Recommendation<T>>(heap);
        Collections.sort(result, Collections.reverseOrder(byScore));
        return result;
    }

    /** 사전 계산된 반경 내 급속 충전소 수 */
    public int fastChargersNearby(String id) {
        int idx = indexOf(id);
        return idx < 0 ? 0 : chargerCounts[idx];
    }

    public T find(String id) {
        int idx = indexOf(id);
        return idx < 0 ? null : items.get(idx);
    }

    /** 점수 오름차순 (같으면 id 내림차순) — 최소 힙의 맨 위가 "가장 덜 추천할" 항목이 된다 */
    private final Comparator<Recommendation<T>> byScore = new Comparator<Recommendation<T>>() {
        @Override
        public int compare(Recommendation<T> a, Recommendation<T> b) {
            int c = Double.compare(a.score, b.score);
            return c != 0 ? c : b.item.getId().compareTo(a.item.getId());
        }
    };

    private int indexOf(String id) {
        Integer idx = indexById.get(id);
        return idx == null ? -1 : idx;
    }

    static Map<String, Double> unitVector(Collection<String> features) {
        Map<String, Double> v = new HashMap<String, Double>();
        if (features == null) return v;
        for (String f : features) {
            if (f != null && !f.isEmpty()) v.put(f, 1.0);
        }
        double n = norm(v);
        if (n > 0) for (Map.Entry<String, Double> e : v.entrySet()) e.setValue(e.getValue() / n);
        return v;
    }

    static double cosine(Map<String, Double> a, Map<String, Double> b) {
        double na = norm(a);
        double nb = norm(b);
        return na == 0 || nb == 0 ? 0 : dot(a, b) / (na * nb);
    }

    private static double dot(Map<String, Double> a, Map<String, Double> b) {
        Map<String, Double> small = a.size() <= b.size() ? a : b;
        Map<String, Double> large = small == a ? b : a;
        double sum = 0;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double other = large.get(e.getKey());
            if (other != null) sum += e.getValue() * other;
        }
        return sum;
    }

    private static double norm(Map<String, Double> v) {
        double sum = 0;
        for (double x : v.values()) sum += x * x;
        return Math.sqrt(sum);
    }

    private static void addInto(Map<String, Double> target, Map<String, Double> source, double scale) {
        for (Map.Entry<String, Double> e : source.entrySet()) {
            if (e.getValue() == null || e.getValue().isNaN()) throw new IllegalArgumentException("가중치가 올바르지 않습니다: " + e.getKey());
            Double cur = target.get(e.getKey());
            target.put(e.getKey(), (cur == null ? 0 : cur) + e.getValue() * scale);
        }
    }
}
