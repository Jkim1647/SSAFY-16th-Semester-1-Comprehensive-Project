package com.ssafy.enjoytrip.filter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 상태를 바꾸는 요청(POST 등)은 세션 토큰과 같은 값을 폼 필드 _csrf 나 헤더 X-CSRF-Token 으로 보내야 통과한다.
 * 다른 사이트가 로그인된 사용자 브라우저로 몰래 글쓰기·탈퇴 요청을 보내는 것(CSRF)을 막는다.
 * 토큰은 header.jsp 가 meta 태그와 폼 hidden 필드에 넣고, common.js 의 api.post 가 헤더로 보낸다.
 */
@WebFilter("/*")
public class CsrfFilter extends HttpFilter {

    private static final long serialVersionUID = 1L;
    public static final String TOKEN_ATTR = "csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String method = req.getMethod();
        boolean safe = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
        if (safe) {
            if (!isStatic(req)) {
                ensureToken(req.getSession());
            }
            chain.doFilter(req, res);
            return;
        }
        HttpSession session = req.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(TOKEN_ATTR);
        String actual = req.getHeader("X-CSRF-Token");
        if (actual == null) {
            try {
                actual = req.getParameter("_csrf");
            } catch (IllegalStateException e) {
                // Tomcat 11: 업로드 크기 초과·깨진 인코딩이면 파라미터 파싱 자체가 실패한다 → 500 대신 원인을 알려 준다
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "요청을 읽을 수 없습니다. 사진은 5MB 이하로 올려 주세요.");
                return;
            }
        }
        if (expected == null || actual == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "요청이 만료되었습니다. 페이지를 새로고침한 뒤 다시 시도하세요.");
            return;
        }
        chain.doFilter(req, res);
    }

    private static boolean isStatic(HttpServletRequest req) {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        return path.startsWith("/assets/") || path.startsWith("/uploads/") || path.equals("/favicon.ico");
    }

    private static void ensureToken(HttpSession session) {
        if (session.getAttribute(TOKEN_ATTR) == null) {
            byte[] b = new byte[24];
            RANDOM.nextBytes(b);
            session.setAttribute(TOKEN_ATTR, Base64.getUrlEncoder().withoutPadding().encodeToString(b));
        }
    }
}
