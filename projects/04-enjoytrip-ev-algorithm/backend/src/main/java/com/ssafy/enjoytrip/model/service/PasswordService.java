package com.ssafy.enjoytrip.model.service;

import com.ssafy.enjoytrip.algorithm.security.PasswordHasher;
import com.ssafy.enjoytrip.config.EnjoyTripProperties;
import com.ssafy.enjoytrip.exception.TooManyRequestsException;
import com.ssafy.enjoytrip.model.dto.PasswordDtos.HashResult;
import com.ssafy.enjoytrip.model.dto.PasswordDtos.VerifyResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * #3 비밀번호 해시. 회원 기능이 생기면 회원가입은 hash(), 로그인은 verify() → needsRehash() 순서로 쓴다.
 * 비밀번호 평문은 로그에 남기지 않는다.
 *
 * <p>해시 1회는 키 스트레칭 때문에 일부러 수백 ms 의 CPU 를 쓴다. 요청이 몰려도 서버를 붙잡지 않도록
 * <ul>
 *   <li>동시에 계산하는 개수를 제한하고, 자리가 없으면 <b>기다리지 않고 바로</b> 429 로 돌려보낸다
 *       (기다리게 하면 요청 스레드가 묶여 검색 등 다른 API 까지 멈출 수 있다).</li>
 *   <li>검증할 저장값의 반복 횟수는 서버 설정값까지만 받는다. 서버가 만드는 해시는 그보다 클 수 없으므로,
 *       더 큰 값은 계산 전에 거부해 한 요청이 계산 자리를 오래 차지하지 못하게 한다.</li>
 * </ul>
 * 운영에서는 IP·계정별 요청 횟수 제한도 함께 둔다.
 */
@Service
public class PasswordService {

    private final PasswordHasher hasher;
    private final boolean demoPepper;
    private final Semaphore slots;

    public PasswordService(PasswordHasher hasher, EnjoyTripProperties properties) {
        this.hasher = hasher;
        String pepper = properties.getPassword().getPepper();
        this.demoPepper = pepper == null || pepper.isEmpty();
        int configured = properties.getPassword().getMaxConcurrent();
        int permits = configured > 0 ? configured : Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        this.slots = new Semaphore(permits);
    }

    public HashResult hash(String password) {
        return limited(() -> {
            long t0 = System.nanoTime();
            String stored = hasher.hash(password);
            double ms = (System.nanoTime() - t0) / 1e6;
            String[] parts = stored.split("\\$");
            return new HashResult(stored, parts[0], Long.parseLong(parts[1]), parts[2], parts[3], ms);
        });
    }

    public VerifyResult verify(String password, String stored) {
        if (PasswordHasher.iterationsOf(stored) > hasher.getIterations()) {
            throw new IllegalArgumentException("이 서버가 만든 해시가 아닙니다 (반복 횟수가 서버 설정보다 큼).");
        }
        return limited(() -> {
            long t0 = System.nanoTime();
            boolean ok = hasher.verify(password, stored);
            return new VerifyResult(ok, (System.nanoTime() - t0) / 1e6);
        });
    }

    private <R> R limited(Supplier<R> work) {
        if (!slots.tryAcquire()) throw new TooManyRequestsException("요청이 많아 처리하지 못했습니다. 잠시 뒤 다시 시도하세요.");
        try {
            return work.get();
        } finally {
            slots.release();
        }
    }

    public int iterations() {
        return hasher.getIterations();
    }

    public boolean isDemoPepper() {
        return demoPepper;
    }
}
