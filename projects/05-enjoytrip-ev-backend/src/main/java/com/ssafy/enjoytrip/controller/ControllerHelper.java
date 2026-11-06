package com.ssafy.enjoytrip.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.ssafy.enjoytrip.model.dto.Member;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 모든 컨트롤러가 공유하는 요청·응답 도우미 (수업 BE_06 ControllerHelper 확장). */
public interface ControllerHelper {

    ObjectMapper MAPPER = JsonMapper.builder().build();
    String LOGIN_USER = "loginUser";

    default String getActionParameter(HttpServletRequest request, HttpServletResponse response) {
        String action = request.getParameter("action");
        return action == null || action.isBlank() ? "index" : action;
    }

    default void redirect(HttpServletRequest request, HttpServletResponse response, String path) throws IOException {
        // 외부 주소로의 리다이렉트는 허용하지 않는다 (open redirect 방지)
        response.sendRedirect(request.getContextPath() + (path.startsWith("/") ? path : "/" + path));
    }

    default void forward(HttpServletRequest request, HttpServletResponse response, String path)
            throws ServletException, IOException {
        RequestDispatcher disp = request.getRequestDispatcher(path);
        disp.forward(request, response);
    }

    /** /WEB-INF/views/{name}.jsp 로 forward. JSP 를 WEB-INF 아래 두어 컨트롤러를 거치지 않은 직접 접근을 막는다. */
    default void view(HttpServletRequest request, HttpServletResponse response, String name)
            throws ServletException, IOException {
        forward(request, response, "/WEB-INF/views/" + name + ".jsp");
    }

    default void toJSON(Object target, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(target));
    }

    /** JSP 의 <script type="application/json"> 안에 넣을 JSON. "</script>" 로 태그가 끊기지 않게 < 를 이스케이프한다. */
    default String jsonForScript(Object target) {
        return MAPPER.writeValueAsString(target).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }

    default void jsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        toJSON(Map.of("error", message), response);
    }

    /** 다음 화면에서 한 번 띄울 알림 (header.jsp 가 꺼내 보여주고 지운다). */
    default void flash(HttpServletRequest request, String message) {
        request.getSession().setAttribute("alertMsg", message);
    }

    default Member loginUser(HttpServletRequest request) {
        var session = request.getSession(false);
        return session == null ? null : (Member) session.getAttribute(LOGIN_USER);
    }

    default boolean isAjax(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return "fetch".equals(request.getHeader("X-Requested-With")) || (accept != null && accept.contains("application/json"));
    }

    /** 로그인하지 않았으면 로그인 화면으로 보내고(AJAX 는 401) false 를 돌려준다. */
    default boolean requireLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (loginUser(request) != null) {
            return true;
        }
        if (isAjax(request)) {
            jsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다.");
        } else {
            flash(request, "로그인 후 사용하세요.");
            String back = request.getRequestURI().substring(request.getContextPath().length())
                    + (request.getQueryString() == null || !"GET".equals(request.getMethod()) ? "" : "?" + request.getQueryString());
            redirect(request, response, "/member?action=login-form&next=" + URLEncoder.encode(back, StandardCharsets.UTF_8));
        }
        return false;
    }

    default boolean requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireLogin(request, response)) {
            return false;
        }
        if (loginUser(request).isAdmin()) {
            return true;
        }
        if (isAjax(request)) {
            jsonError(response, HttpServletResponse.SC_FORBIDDEN, "관리자만 사용할 수 있습니다.");
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
        return false;
    }

    default int intParam(HttpServletRequest request, String name, int defaultValue) {
        String v = request.getParameter(name);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    default long longParam(HttpServletRequest request, String name, long defaultValue) {
        String v = request.getParameter(name);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    default String param(HttpServletRequest request, String name) {
        String v = request.getParameter(name);
        return v == null ? "" : v.trim();
    }
}
