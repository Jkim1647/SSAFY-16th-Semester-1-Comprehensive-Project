package com.ssafy.enjoytrip.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordResetUtilTest {

    @Test
    void resetTokensAreLongRandomAndStoredOnlyAsHash() {
        String a = Passwords.resetToken();
        String b = Passwords.resetToken();
        assertNotEquals(a, b);
        assertEquals(43, a.length(), "32바이트 → URL 안전 Base64 43자");
        String hash = Passwords.sha256Hex(a);
        assertEquals(64, hash.length());
        assertEquals(hash, Passwords.sha256Hex(a));
        assertFalse(hash.contains(a));
    }

    @Test
    void rateLimiterBlocksAfterLimitPerKey() {
        RateLimiter limiter = new RateLimiter(3, 60_000);
        assertTrue(limiter.tryAcquire("a@x.kr"));
        assertTrue(limiter.tryAcquire("a@x.kr"));
        assertTrue(limiter.tryAcquire("a@x.kr"));
        assertFalse(limiter.tryAcquire("a@x.kr"), "같은 키 4번째 요청은 막혀야 한다");
        assertTrue(limiter.tryAcquire("b@x.kr"), "다른 키는 영향을 받지 않는다");
    }
}
