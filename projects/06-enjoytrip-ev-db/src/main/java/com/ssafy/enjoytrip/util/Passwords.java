package com.ssafy.enjoytrip.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import com.ssafy.enjoytrip.algorithm.security.PasswordHasher;
import com.ssafy.enjoytrip.config.AppConfig;

/** 4회차 알고리즘 PJT 의 PBKDF2 + salt + pepper 해시를 서버 설정으로 감싼 진입점. */
public final class Passwords {

    private static final PasswordHasher HASHER = new PasswordHasher(
            AppConfig.get("security.pepper").getBytes(StandardCharsets.UTF_8),
            AppConfig.getInt("security.iterations", PasswordHasher.DEFAULT_ITERATIONS),
            new SecureRandom());

    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    public static String hash(String raw) {
        return HASHER.hash(raw);
    }

    /** 저장값의 반복 횟수가 서버 설정보다 크면 계산하지 않고 거부한다 (변조된 값으로 CPU 를 붙잡는 것 방지). */
    public static boolean verify(String raw, String stored) {
        if (raw == null || raw.isEmpty() || raw.length() > PasswordHasher.MAX_PASSWORD_LENGTH || stored == null) {
            return false;
        }
        try {
            if (PasswordHasher.iterationsOf(stored) > HASHER.getIterations()) {
                return false;
            }
            return HASHER.verify(raw, stored);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean needsRehash(String stored) {
        return HASHER.needsRehash(stored);
    }

    /** 비밀번호 재설정 링크에 넣을 토큰 (256비트 난수, URL 안전 Base64). 원문은 DB 에 저장하지 않는다. */
    public static String resetToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** 토큰을 DB 에 저장·조회할 때 쓰는 SHA-256 16진수 해시. 고엔트로피 난수라 salt·반복이 필요 없다. */
    public static String sha256Hex(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte x : d) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
