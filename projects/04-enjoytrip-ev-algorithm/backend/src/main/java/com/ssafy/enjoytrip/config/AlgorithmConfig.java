package com.ssafy.enjoytrip.config;

import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlanner;
import com.ssafy.enjoytrip.algorithm.geo.DistanceCalculator;
import com.ssafy.enjoytrip.algorithm.geo.HaversineDistance;
import com.ssafy.enjoytrip.algorithm.route.RouteOptimizer;
import com.ssafy.enjoytrip.algorithm.security.PasswordHasher;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * algorithm-core 의 알고리즘 객체를 싱글턴 Bean 으로 등록한다.
 * 알고리즘 객체는 상태가 없어(또는 만든 뒤 읽기만 해서) 여러 요청이 동시에 써도 안전하다.
 * 데이터가 필요한 Trie·추천기 등은 데이터를 읽은 뒤 AttractionService 가 만든다.
 */
@Configuration
public class AlgorithmConfig {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmConfig.class);
    static final String DEMO_PEPPER = "demo-pepper-not-for-production";

    /** 직선거리 × 1.3. 실제 도로 거리가 필요하면 KakaoRoadDistance 로 바꿔 끼운다 (알고리즘 코드는 그대로). */
    @Bean
    public DistanceCalculator distanceCalculator() {
        return new HaversineDistance();
    }

    @Bean
    public EvTripPlanner evTripPlanner(DistanceCalculator distance) {
        return new EvTripPlanner(new RouteOptimizer(distance), new EvRoutePlanner(distance));
    }

    @Bean
    public PasswordHasher passwordHasher(EnjoyTripProperties properties) {
        String pepper = properties.getPassword().getPepper();
        if (pepper == null || pepper.isEmpty()) {
            log.warn("enjoytrip.password.pepper(ENJOYTRIP_PEPPER) 가 없어 시연용 pepper 를 씁니다. 운영에서는 반드시 설정하세요.");
            pepper = DEMO_PEPPER;
        }
        return new PasswordHasher(pepper.getBytes(StandardCharsets.UTF_8), properties.getPassword().getIterations(), new SecureRandom());
    }
}
