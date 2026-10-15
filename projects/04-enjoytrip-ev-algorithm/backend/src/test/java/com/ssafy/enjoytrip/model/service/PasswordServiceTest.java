package com.ssafy.enjoytrip.model.service;

import com.ssafy.enjoytrip.algorithm.security.PasswordHasher;
import com.ssafy.enjoytrip.config.EnjoyTripProperties;
import com.ssafy.enjoytrip.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 해시 계산이 몰리거나 무거운 저장값이 와도 서버를 오래 붙잡지 않는지 확인 */
class PasswordServiceTest {

    @Test
    void rejectsImmediatelyWhenAllSlotsAreBusy() throws Exception {
        EnjoyTripProperties props = new EnjoyTripProperties();
        props.getPassword().setMaxConcurrent(1);
        // 반복 횟수를 크게 해 첫 계산이 끝나기 전에 두 번째 요청이 오게 한다
        PasswordHasher slow = new PasswordHasher("p".getBytes(StandardCharsets.UTF_8), 3_000_000, new SecureRandom());
        PasswordService service = new PasswordService(slow, props);

        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> {
            started.countDown();
            service.hash("first-password");
        });
        started.await();
        Thread.sleep(100);
        long t0 = System.nanoTime();
        assertThatThrownBy(() -> service.hash("second-password")).isInstanceOf(TooManyRequestsException.class);
        assertThat((System.nanoTime() - t0) / 1e6).as("기다리지 않고 바로 거절").isLessThan(50);
        first.get();
        // 자리가 나면 다시 처리된다
        assertThat(service.verify("first-password", service.hash("first-password").stored()).match()).isTrue();
    }

    @Test
    void rejectsStoredHashHeavierThanServerSetting() {
        EnjoyTripProperties props = new EnjoyTripProperties();
        PasswordHasher server = new PasswordHasher("p".getBytes(StandardCharsets.UTF_8), 1000, new SecureRandom());
        PasswordService service = new PasswordService(server, props);
        String stored = service.hash("enjoytrip").stored();
        // 서버 설정(1000)보다 큰 반복 횟수(상한 1,000만 이내)로 바꾼 저장값은 계산 없이 거부
        String heavy = stored.replace("$1000$", "$9000000$");
        long t0 = System.nanoTime();
        assertThatThrownBy(() -> service.verify("enjoytrip", heavy)).isInstanceOf(IllegalArgumentException.class);
        assertThat((System.nanoTime() - t0) / 1e6).isLessThan(50);
        // 설정 이하(예전 기준으로 만든 해시)는 정상 검증
        assertThat(service.verify("enjoytrip", stored).match()).isTrue();
    }
}
