package com.ssafy.enjoytrip.util;

/**
 * 비밀번호 재설정 링크 전달. 수업 환경에는 메일 서버가 없어 서버 콘솔에 남긴다 (요청한 브라우저에는 절대 보여주지 않는다).
 * 운영에서는 이 클래스만 SMTP(Jakarta Mail)나 메일 API 호출로 바꾸면 된다.
 */
public final class ResetMailer {

    private ResetMailer() {
    }

    public static void send(String email, String link, int validMinutes) {
        System.out.printf("[EnjoyTrip][메일 대신 콘솔] %s 님의 비밀번호 재설정 링크 (%d분 동안 1회): %s%n", email, validMinutes, link);
    }
}
