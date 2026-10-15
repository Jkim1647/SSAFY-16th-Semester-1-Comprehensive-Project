package com.ssafy.enjoytrip.algorithm.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;

/**
 * PBKDF2 (RFC 8018 §5.2) 직접 구현. 키 스트레칭: 같은 HMAC 을 iterations 번 반복해 해시 한 번에 드는 비용을 일부러 키운다.
 *
 * <pre>
 * DK = T1 || T2 || ... (dkLen 바이트까지)
 * Ti = U1 ^ U2 ^ ... ^ Uc
 * U1 = PRF(P, S || INT(i)),  Uj = PRF(P, U(j-1))
 * </pre>
 * 시간 O(iterations · 블록 수), 메모리 O(dkLen).
 * JDK 의 SecretKeyFactory("PBKDF2WithHmacSHA256") 와 같은 결과를 내는지 테스트로 확인한다.
 */
public final class Pbkdf2 {

    public static final String HMAC_SHA256 = "HmacSHA256";

    private Pbkdf2() {
    }

    public static byte[] derive(String hmacAlgorithm, byte[] password, byte[] salt, int iterations, int dkLen) {
        if (password == null || password.length == 0) throw new IllegalArgumentException("password 가 비어 있습니다.");
        if (salt == null) throw new IllegalArgumentException("salt 가 null 입니다.");
        if (iterations < 1) throw new IllegalArgumentException("iterations 는 1 이상이어야 합니다.");
        if (dkLen < 1) throw new IllegalArgumentException("dkLen 은 1 이상이어야 합니다.");

        Mac prf = newMac(hmacAlgorithm, password);
        int hLen = prf.getMacLength();
        int blocks = (dkLen + hLen - 1) / hLen;
        byte[] dk = new byte[dkLen];
        byte[] u = new byte[hLen];
        byte[] t = new byte[hLen];

        for (int i = 1; i <= blocks; i++) {
            prf.update(salt);
            prf.update(new byte[]{(byte) (i >>> 24), (byte) (i >>> 16), (byte) (i >>> 8), (byte) i});
            doFinal(prf, u);
            System.arraycopy(u, 0, t, 0, hLen);
            for (int j = 2; j <= iterations; j++) {
                prf.update(u);
                doFinal(prf, u);
                for (int k = 0; k < hLen; k++) t[k] ^= u[k];
            }
            int offset = (i - 1) * hLen;
            System.arraycopy(t, 0, dk, offset, Math.min(hLen, dkLen - offset));
        }
        return dk;
    }

    static Mac newMac(String algorithm, byte[] key) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac;
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("HMAC 키가 올바르지 않습니다.", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 알고리즘을 사용할 수 없습니다: " + algorithm, e);
        }
    }

    private static void doFinal(Mac mac, byte[] out) {
        try {
            mac.doFinal(out, 0);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
