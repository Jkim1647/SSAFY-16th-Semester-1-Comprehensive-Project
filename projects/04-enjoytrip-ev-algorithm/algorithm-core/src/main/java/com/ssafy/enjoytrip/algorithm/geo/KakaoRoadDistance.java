package com.ssafy.enjoytrip.algorithm.geo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 카카오모빌리티 길찾기 API 로 구한 실제 도로 거리(km).
 * 하버사인(직선) 거리는 바다·산을 가로지르는 "날아가는 거리"라 도로 거리와 차이가 크다는 한계를 보완한다.
 *
 * <ul>
 *   <li>같은 두 지점은 다시 묻지 않도록 캐시한다 (TSP 거리 행렬은 n(n-1)/2 번만 호출).</li>
 *   <li>알고리즘들이 대칭 거리를 가정하므로 (a,b) 와 (b,a) 를 같은 키로 캐시한다. 실제 도로는 일방통행 등으로 약간 다를 수 있다.</li>
 *   <li>API 실패(키 오류, 경로 없음, 네트워크)는 fallback 거리(하버사인 × 1.3)로 대신하고, 횟수와 마지막 오류를 남긴다.
 *       조용히 넘기지 않도록 호출한 쪽에서 {@link #getFallbackCount()} 를 확인해 응답에 표시하면 된다.</li>
 * </ul>
 * REST 키는 코드에 넣지 말고 환경 변수(KAKAO_REST_KEY)나 서버 설정에서 받는다.
 */
public class KakaoRoadDistance implements DistanceCalculator {

    static final String ENDPOINT = "https://apis-navi.kakaomobility.com/v1/directions";
    private static final Pattern RESULT_CODE = Pattern.compile("\"result_code\"\\s*:\\s*(-?\\d+)");
    private static final Pattern DISTANCE = Pattern.compile("\"distance\"\\s*:\\s*(\\d+)");

    private final String restKey;
    private final DistanceCalculator fallback;
    private final int timeoutMs;
    private final Map<String, Double> cache = new ConcurrentHashMap<String, Double>();
    private final AtomicInteger apiCalls = new AtomicInteger();
    private final AtomicInteger fallbackCount = new AtomicInteger();
    private volatile String lastError;

    public KakaoRoadDistance(String restKey) {
        this(restKey, new HaversineDistance(), 5000);
    }

    public KakaoRoadDistance(String restKey, DistanceCalculator fallback, int timeoutMs) {
        if (restKey == null || restKey.trim().isEmpty()) throw new IllegalArgumentException("카카오 REST 키가 없습니다.");
        if (fallback == null) throw new IllegalArgumentException("fallback 이 null 입니다.");
        this.restKey = restKey.trim();
        this.fallback = fallback;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public double km(Location a, Location b) {
        if (a.getLat() == b.getLat() && a.getLng() == b.getLng()) return 0;
        String key = cacheKey(a, b);
        Double cached = cache.get(key);
        if (cached != null) return cached;
        double km;
        try {
            km = request(a, b);
        } catch (IOException | RuntimeException e) {
            fallbackCount.incrementAndGet();
            lastError = e.getMessage();
            km = fallback.km(a, b);
        }
        cache.put(key, km);
        return km;
    }

    private double request(Location a, Location b) throws IOException {
        String url = String.format(Locale.ROOT, "%s?origin=%.7f,%.7f&destination=%.7f,%.7f&summary=true",
                ENDPOINT, a.getLng(), a.getLat(), b.getLng(), b.getLat());
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("Authorization", "KakaoAK " + restKey);
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        apiCalls.incrementAndGet();
        try {
            int status = conn.getResponseCode();
            InputStream in = status < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = in == null ? "" : read(in);
            if (status != 200) throw new IOException("카카오모빌리티 응답 " + status);
            return parseDistanceKm(body);
        } finally {
            conn.disconnect();
        }
    }

    /** 응답에서 routes[0].summary.distance(m) 를 km 로. result_code 가 0 이 아니면(경로 없음 등) 예외 */
    static double parseDistanceKm(String body) {
        Matcher code = RESULT_CODE.matcher(body);
        if (!code.find()) throw new IllegalStateException("응답 형식이 올바르지 않습니다.");
        if (!"0".equals(code.group(1))) throw new IllegalStateException("길찾기 실패 result_code=" + code.group(1));
        int summary = body.indexOf("\"summary\"");
        if (summary < 0) throw new IllegalStateException("응답에 summary 가 없습니다.");
        Matcher d = DISTANCE.matcher(body);
        if (!d.find(summary)) throw new IllegalStateException("응답에 distance 가 없습니다.");
        return Long.parseLong(d.group(1)) / 1000.0;
    }

    private static String read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static String cacheKey(Location a, Location b) {
        String ka = String.format(Locale.ROOT, "%.6f,%.6f", a.getLat(), a.getLng());
        String kb = String.format(Locale.ROOT, "%.6f,%.6f", b.getLat(), b.getLng());
        return ka.compareTo(kb) <= 0 ? ka + "|" + kb : kb + "|" + ka;
    }

    public int getApiCalls() {
        return apiCalls.get();
    }

    public int getFallbackCount() {
        return fallbackCount.get();
    }

    public String getLastError() {
        return lastError;
    }
}
