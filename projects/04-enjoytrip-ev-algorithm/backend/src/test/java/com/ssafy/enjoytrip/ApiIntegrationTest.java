package com.ssafy.enjoytrip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 데이터(data/*.csv)로 Spring 을 띄워 API 를 호출한다. 알고리즘 자체의 정확성은 algorithm-core 테스트가 맡고,
 * 여기서는 Controller → Service → DAO 연결, 응답 형식, 입력 오류 처리를 확인한다.
 * 비밀번호 반복 횟수는 테스트 속도를 위해 줄인다.
 */
@SpringBootTest(properties = "enjoytrip.password.iterations=1000")
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void metaReturnsDataCountsAndCategories() throws Exception {
        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attractionCount").value(1504))
                .andExpect(jsonPath("$.categories[0].name").isNotEmpty())
                .andExpect(jsonPath("$.demoPepper").value(true));
    }

    @Test
    void autocompleteSupportsChoseong() throws Exception {
        JsonNode items = json(mvc.perform(get("/api/attractions/autocomplete").param("q", "ㄷㅇ").param("limit", "5"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("items");
        assertThat(items).hasSize(5);
        for (JsonNode spot : items) assertThat(spot.get("name").asText()).isNotEmpty();
        mvc.perform(get("/api/attractions/autocomplete").param("q", "ㄷㅇ").param("limit", "99")).andExpect(status().isBadRequest());
    }

    @Test
    void searchFindsKeywordWithPositions() throws Exception {
        mvc.perform(get("/api/attractions/search").param("q", "국립공원"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(8))
                .andExpect(jsonPath("$.items[0].field").value("name"))
                .andExpect(jsonPath("$.items[0].spot.name").isNotEmpty())
                .andExpect(jsonPath("$.items[0].positions[0]").isNumber());
    }

    @Test
    void searchFallsBackToKeyboardFixThenFuzzy() throws Exception {
        // 정확히 있으면 exact
        mvc.perform(get("/api/attractions/search").param("q", "국립공원"))
                .andExpect(jsonPath("$.mode").value("exact"))
                .andExpect(jsonPath("$.items[0].distance").value(0));
        // 영문 상태로 친 "rnrflqrhddnjs" → "국립공원" 으로 고쳐 정확 일치
        mvc.perform(get("/api/attractions/search").param("q", "rnrflqrhddnjs"))
                .andExpect(jsonPath("$.mode").value("keyboard"))
                .andExpect(jsonPath("$.correctedQuery").value("국립공원"))
                .andExpect(jsonPath("$.items.length()").value(8));
        // 받침 하나 틀린 "국림공원" → 오타 허용 검색, 혹시 "국립공원"?
        JsonNode fuzzy = json(mvc.perform(get("/api/attractions/search").param("q", "국림공원"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(fuzzy.get("mode").asText()).isEqualTo("fuzzy");
        assertThat(fuzzy.get("suggestion").asText()).isEqualTo("국립공원");
        assertThat(fuzzy.get("items").size()).isEqualTo(8);
        for (JsonNode hit : fuzzy.get("items")) {
            assertThat(hit.get("distance").asInt()).isEqualTo(1);
            String name = hit.get("spot").get("name").asText();
            int p = hit.get("positions").get(0).asInt();
            assertThat(name.substring(p, p + hit.get("keywordLength").asInt())).isEqualTo("국립공원");
        }
        // 한/영 전환 오타는 자동완성에서도 고친다
        mvc.perform(get("/api/attractions/autocomplete").param("q", "ekadid"))
                .andExpect(jsonPath("$.correctedQuery").value("담양"))
                .andExpect(jsonPath("$.items[0].name").isNotEmpty());
    }

    @Test
    void rejectsTooLongSearchQuery() throws Exception {
        String longQuery = "가".repeat(31);
        mvc.perform(get("/api/attractions/search").param("q", longQuery)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/attractions/autocomplete").param("q", longQuery)).andExpect(status().isBadRequest());
        // 30자까지는 정상 처리 (가장 긴 자모 조합 글자로도 오타 검색 상한 160키를 넘지 않음)
        mvc.perform(get("/api/attractions/search").param("q", "뷁".repeat(30))).andExpect(status().isOk());
    }

    @Test
    void listSortsByChargersDescending() throws Exception {
        JsonNode items = json(mvc.perform(get("/api/attractions").param("by", "chargers").param("limit", "20"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("items");
        for (int i = 1; i < items.size(); i++) {
            assertThat(items.get(i - 1).get("spot").get("fastChargersNearby").asInt())
                    .isGreaterThanOrEqualTo(items.get(i).get("spot").get("fastChargersNearby").asInt());
        }
        mvc.perform(get("/api/attractions").param("by", "price")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void recommendRespectsPreferenceAndRejectsDistanceOnlyWithoutBase() throws Exception {
        mvc.perform(get("/api/attractions/recommend").param("prefer", "HS").param("lat", "35.15").param("lng", "126.85"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].spot.category").value("역사관광"))
                .andExpect(jsonPath("$.items[0].km").isNumber());
        // 리뷰에서 찾은 NaN 사례: 기준 위치 없이 거리 가중치만 → 400
        mvc.perform(get("/api/attractions/recommend").param("wTaste", "0").param("wDistance", "1").param("wCharger", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordHashThenVerify() throws Exception {
        JsonNode hashed = json(mvc.perform(post("/api/password/hash").param("password", "enjoytrip2026!"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String stored = hashed.get("stored").asText();
        assertThat(stored).startsWith("pbkdf2_sha256$1000$").doesNotContain("enjoytrip2026");
        mvc.perform(post("/api/password/verify").param("password", "enjoytrip2026!").param("stored", stored))
                .andExpect(jsonPath("$.match").value(true));
        mvc.perform(post("/api/password/verify").param("password", "wrong").param("stored", stored))
                .andExpect(jsonPath("$.match").value(false));
        // 반복 횟수를 키운 변조 저장값은 계산 없이 400
        mvc.perform(post("/api/password/verify").param("password", "x").param("stored", stored.replace("$1000$", "$2000000000$")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tripPlanMatchesConsoleDemo() throws Exception {
        String request = """
                {"start":{"name":"광주송정역","lat":35.1378,"lng":126.7932},
                 "attractionIds":["128834","126730","2606207","128025","129024","127931","575607","127869","126375","127715"],
                 "returnToStart":true,"startSoc":60}""";
        JsonNode plan = json(mvc.perform(post("/api/trips/plan").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // 콘솔 시연(AlgorithmDemo)과 같은 결과: Held-Karp, 570.3km 순서, 충전 2회, 총 570.9km
        assertThat(plan.get("feasible").asBoolean()).isTrue();
        assertThat(plan.get("method").asText()).isEqualTo("HELD_KARP");
        assertThat(plan.get("routeKm").asDouble()).isBetween(570.2, 570.4);
        assertThat(plan.get("totalKm").asDouble()).isBetween(570.8, 571.0);
        assertThat(plan.get("charges").asInt()).isEqualTo(2);
        assertThat(plan.get("legs")).hasSize(11);
    }

    @Test
    void tripPlanRejectsBadInput() throws Exception {
        mvc.perform(post("/api/trips/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":{\"lat\":35.1,\"lng\":126.8},\"attractionIds\":[\"nope\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("없는 관광지입니다: nope"));
        mvc.perform(post("/api/trips/plan").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void servesDemoPage() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }
}
