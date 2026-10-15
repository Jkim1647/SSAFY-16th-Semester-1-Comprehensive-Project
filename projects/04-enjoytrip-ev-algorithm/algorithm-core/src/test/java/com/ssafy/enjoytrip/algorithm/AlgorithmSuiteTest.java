package com.ssafy.enjoytrip.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * mvn test 에서 실행되는 진입점. 기존 검증(완전탐색·JDK·RFC 테스트 벡터 비교 28개)을 그대로 돌리고,
 * 하나라도 실패하면 실패 이름과 메시지를 모아 보여준다.
 */
class AlgorithmSuiteTest {

    @Test
    void allAlgorithmChecksPass() {
        List<String> failures = AlgorithmTest.runAll();
        assertTrue(failures.isEmpty(), "실패한 검증:\n  - " + String.join("\n  - ", failures));
    }
}
