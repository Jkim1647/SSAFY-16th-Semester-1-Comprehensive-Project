package com.ssafy.enjoytrip.algorithm.security;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 회원 비밀번호 저장용 단방향 해시 (평문 저장 금지).
 *
 * <ol>
 *   <li>pepper: 서버 설정(환경 변수)에만 있는 공용 비밀 값. DB 가 통째로 유출돼도 pepper 없이는 해시를 다시 계산할 수 없다.
 *       비밀번호에 HMAC-SHA256(pepper, 비밀번호) 로 섞는다.</li>
 *   <li>salt: 사용자마다 새로 만드는 16바이트 난수. 같은 비밀번호도 해시가 달라져 레인보우 테이블이 쓸모없어진다.
 *       salt 는 비밀이 아니므로 해시와 함께 저장한다.</li>
 *   <li>키 스트레칭: PBKDF2-HMAC-SHA256 을 iterations 번 반복 (기본 600,000 — OWASP Password Storage Cheat Sheet 권고값).</li>
 * </ol>
 *
 * 저장 형식: {@code pbkdf2_sha256$반복횟수$salt(Base64)$hash(Base64)} — 반복 횟수를 함께 저장하므로 나중에 기준을 올려도
 * 기존 해시를 검증할 수 있고, {@link #needsRehash} 로 로그인 시점에 새 기준으로 다시 저장할 수 있다.
 *
 * <p>메모리를 많이 쓰게 해 GPU 병렬 공격을 더 어렵게 하는 Argon2 가 더 권장되지만 JDK 에 없어서 외부 라이브러리가 필요하다.
 * BackEnd 에서 Argon2 로 바꾸려면 Spring Security 의 Argon2PasswordEncoder(BouncyCastle) 를 쓰면 된다.
 */
public class PasswordHasher {

    public static final String ALGORITHM_ID = "pbkdf2_sha256";
    public static final int DEFAULT_ITERATIONS = 600_000;
    public static final int SALT_BYTES = 16;
    public static final int HASH_BYTES = 32;
    public static final int MAX_PASSWORD_LENGTH = 128;
    /**
     * 저장값에 적힌 반복 횟수의 상한. 저장값은 DB 변조나 외부 입력으로 들어올 수 있으므로,
     * 상한 없이 그대로 쓰면 반복 횟수 20억 같은 값 하나로 서버 CPU 를 몇 시간씩 붙잡을 수 있다 (DoS).
     */
    public static final int MAX_ITERATIONS = 10_000_000;

    private final byte[] pepper;
    private final int iterations;
    private final SecureRandom random;

    /** @param pepper 서버 비밀 값. null 이면 pepper 없이 동작한다 (운영에서는 반드시 설정). */
    public PasswordHasher(byte[] pepper) {
        this(pepper, DEFAULT_ITERATIONS, new SecureRandom());
    }

    public PasswordHasher(byte[] pepper, int iterations, SecureRandom random) {
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("iterations 는 1~" + MAX_ITERATIONS + " 이어야 합니다.");
        }
        if (random == null) throw new IllegalArgumentException("random 이 null 입니다.");
        if (pepper != null && pepper.length == 0) throw new IllegalArgumentException("pepper 가 비어 있습니다. 쓰지 않으려면 null 을 넘기세요.");
        this.pepper = pepper == null ? null : pepper.clone();
        this.iterations = iterations;
        this.random = random;
    }

    /** 회원가입·비밀번호 변경 시 DB 에 저장할 문자열을 만든다. */
    public String hash(CharSequence password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return ALGORITHM_ID + "$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /** 로그인 시 입력한 비밀번호가 저장된 해시와 같은지 확인한다. 형식이 깨진 값은 false 가 아니라 예외로 알린다. */
    public boolean verify(CharSequence password, String stored) {
        Parsed p = parse(stored);
        byte[] actual = derive(password, p.salt, p.iterations);
        // 비교 시간이 일치한 바이트 수에 따라 달라지지 않도록 상수 시간 비교
        return MessageDigest.isEqual(actual, p.hash);
    }

    /** 저장된 해시의 반복 횟수가 현재 기준보다 낮으면 true — 로그인 성공 직후 hash() 로 다시 저장하면 된다. */
    public boolean needsRehash(String stored) {
        return parse(stored).iterations < iterations;
    }

    public int getIterations() {
        return iterations;
    }

    /**
     * 저장값에 적힌 반복 횟수 (계산 없이 형식만 확인). 서버 설정보다 큰 값을 미리 거부하는 데 쓴다.
     * 형식이 깨져 있으면 IllegalArgumentException.
     */
    public static int iterationsOf(String stored) {
        return parse(stored).iterations;
    }

    private byte[] derive(CharSequence password, byte[] salt, int rounds) {
        if (password == null || password.length() == 0) throw new IllegalArgumentException("비밀번호가 비어 있습니다.");
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 " + MAX_PASSWORD_LENGTH + "자 이하여야 합니다.");
        }
        byte[] raw = password.toString().getBytes(StandardCharsets.UTF_8);
        byte[] input = raw;
        if (pepper != null) {
            Mac mac = Pbkdf2.newMac(Pbkdf2.HMAC_SHA256, pepper);
            input = mac.doFinal(raw);
            Arrays.fill(raw, (byte) 0);
        }
        try {
            return Pbkdf2.derive(Pbkdf2.HMAC_SHA256, input, salt, rounds, HASH_BYTES);
        } finally {
            Arrays.fill(input, (byte) 0);
        }
    }

    private static Parsed parse(String stored) {
        if (stored == null) throw new IllegalArgumentException("저장된 해시가 null 입니다.");
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !ALGORITHM_ID.equals(parts[0])) {
            throw new IllegalArgumentException("지원하지 않는 해시 형식입니다.");
        }
        try {
            int rounds = Integer.parseInt(parts[1]);
            if (rounds < 1 || rounds > MAX_ITERATIONS) throw new IllegalArgumentException("반복 횟수가 허용 범위를 벗어났습니다.");
            Base64.Decoder b64 = Base64.getDecoder();
            byte[] salt = b64.decode(parts[2]);
            byte[] hash = b64.decode(parts[3]);
            if (salt.length != SALT_BYTES || hash.length != HASH_BYTES) throw new IllegalArgumentException("salt·hash 길이가 올바르지 않습니다.");
            return new Parsed(rounds, salt, hash);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("해시 형식이 깨져 있습니다.", e);
        }
    }

    private static final class Parsed {
        final int iterations;
        final byte[] salt;
        final byte[] hash;

        Parsed(int iterations, byte[] salt, byte[] hash) {
            this.iterations = iterations;
            this.salt = salt;
            this.hash = hash;
        }
    }
}
