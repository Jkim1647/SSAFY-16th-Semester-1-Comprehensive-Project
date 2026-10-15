package com.ssafy.enjoytrip.model.dto;

/** 비밀번호 해시 시연 API 응답 (#3). 실제 서비스에서는 회원가입·로그인 처리 안에서만 쓴다. */
public final class PasswordDtos {

    private PasswordDtos() {
    }

    public record HashResult(String stored, String algorithm, long iterations, String salt, String hash, double elapsedMs) {
    }

    public record VerifyResult(boolean match, double elapsedMs) {
    }
}
