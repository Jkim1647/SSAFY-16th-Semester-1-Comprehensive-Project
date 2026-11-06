package com.ssafy.enjoytrip.controller;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import com.ssafy.enjoytrip.config.AppConfig;
import com.ssafy.enjoytrip.model.dto.Member;
import com.ssafy.enjoytrip.model.dto.SearchCondition;
import com.ssafy.enjoytrip.model.service.BasicMemberService;
import com.ssafy.enjoytrip.model.service.EvService;
import com.ssafy.enjoytrip.model.service.MemberService;
import com.ssafy.enjoytrip.model.service.ValidationException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** F107 회원 관리 · F108 로그인 관리 */
@WebServlet("/member")
public class MemberController extends HttpServlet implements ControllerHelper {

    private static final long serialVersionUID = 1L;
    private final MemberService mService = BasicMemberService.getInstance();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        switch (getActionParameter(request, response)) {
            case "login-form" -> view(request, response, "member/login");
            case "regist-form" -> view(request, response, "member/regist");
            case "find-password-form" -> view(request, response, "member/find-password");
            case "reset-form" -> resetForm(request, response);
            case "email-check" -> emailCheck(request, response);
            case "mypage" -> mypage(request, response);
            case "list" -> memberList(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        switch (getActionParameter(request, response)) {
            case "regist" -> regist(request, response);
            case "login" -> login(request, response);
            case "logout" -> logout(request, response);
            case "modify" -> modify(request, response);
            case "withdraw" -> withdraw(request, response);
            case "find-password" -> findPassword(request, response);
            case "reset-password" -> resetPassword(request, response);
            case "admin-delete" -> adminDelete(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void emailCheck(HttpServletRequest request, HttpServletResponse response) throws IOException {
        toJSON(Map.of("canUse", mService.isEmailAvailable(param(request, "email"))), response);
    }

    private void regist(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String email = param(request, "email");
        String name = param(request, "name");
        String pass = request.getParameter("pass");
        try {
            if (pass == null || !pass.equals(request.getParameter("pass2"))) {
                throw new ValidationException("비밀번호 확인이 일치하지 않습니다.");
            }
            mService.regist(email, name, pass);
            flash(request, "가입되었습니다. 로그인 후 이용하세요.");
            redirect(request, response, "/member?action=login-form");
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
            request.setAttribute("email", email);
            request.setAttribute("name", name);
            view(request, response, "member/regist");
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String email = param(request, "email");
        Member member = mService.login(email, request.getParameter("pass"));
        if (member == null) {
            request.setAttribute("error", "이메일 또는 비밀번호가 맞지 않습니다.");
            request.setAttribute("email", email);
            request.setAttribute("next", param(request, "next"));
            view(request, response, "member/login");
            return;
        }
        // 세션 고정 공격 방지: 로그인 전 세션을 버리고 새 세션 ID 로 시작한다
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true).setAttribute(LOGIN_USER, member);
        flash(request, member.getName() + "님, 환영합니다.");
        redirect(request, response, safeNext(param(request, "next")));
    }

    /** 앱 안의 경로를 이루는 문자만 허용. 탭·공백·역슬래시처럼 브라우저가 지우거나 바꿔서 "//외부주소" 가 되는 입력을 막는다. */
    private static final java.util.regex.Pattern SAFE_NEXT = java.util.regex.Pattern.compile("^/(?!/)[A-Za-z0-9/_\\-.~?=&%]*$");

    static String safeNext(String next) {
        return next != null && SAFE_NEXT.matcher(next).matches() ? next : "/";
    }

    private void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        redirect(request, response, "/");
    }

    private void mypage(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        long memberId = loginUser(request).getMemberId();
        request.setAttribute("member", mService.findById(memberId));
        request.setAttribute("reservations", EvService.getInstance().myReservations(memberId));
        view(request, response, "member/mypage");
    }

    private void modify(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        try {
            Member updated = mService.modify(loginUser(request).getMemberId(), param(request, "name"),
                    request.getParameter("currentPass"), request.getParameter("newPass"));
            request.getSession().setAttribute(LOGIN_USER, updated);
            flash(request, "회원 정보를 수정했습니다.");
        } catch (ValidationException e) {
            flash(request, e.getMessage());
        }
        redirect(request, response, "/member?action=mypage");
    }

    private void withdraw(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        try {
            mService.withdraw(loginUser(request).getMemberId(), request.getParameter("pass"));
            request.getSession().invalidate();
            flash(request, "탈퇴 처리되었습니다. 그동안 이용해 주셔서 감사합니다.");
            redirect(request, response, "/");
        } catch (ValidationException e) {
            flash(request, e.getMessage());
            redirect(request, response, "/member?action=mypage");
        }
    }

    private static final java.util.Set<String> LOCAL_HOSTS = java.util.Set.of("localhost", "127.0.0.1", "[::1]");

    /**
     * 재설정 링크의 기준 주소. Host 헤더는 요청자가 마음대로 바꿀 수 있어서(링크를 공격자 서버로 바꾸는 재설정 링크 오염),
     * app.base-url 설정값만 쓴다. 개발 편의를 위해 설정이 없을 때는 로컬 주소로 접속한 경우에만 요청 주소를 쓰고,
     * 그 밖에는 링크를 만들지 않는다(fail closed).
     */
    private String resetUrlBase(HttpServletRequest request) {
        String base = AppConfig.get("app.base-url");
        if (base.isBlank()) {
            if (!LOCAL_HOSTS.contains(request.getServerName())) {
                throw new IllegalStateException("app.base-url 이 설정되지 않아 재설정 링크를 만들 수 없습니다.");
            }
            base = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
        }
        return base.replaceAll("/+$", "") + "/member?action=reset-form&token=";
    }

    private void findPassword(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            mService.requestPasswordReset(param(request, "email"), param(request, "name"), request.getRemoteAddr(),
                    resetUrlBase(request));
            request.setAttribute("sent", true);
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println("[EnjoyTrip] " + e.getMessage() + " (요청 Host: " + request.getServerName() + ")");
            request.setAttribute("error", "지금은 비밀번호 재설정을 이용할 수 없습니다. 관리자에게 문의하세요.");
        }
        view(request, response, "member/find-password");
    }

    private void resetForm(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String token = param(request, "token");
        request.setAttribute("token", token);
        request.setAttribute("valid", mService.isResetTokenValid(token));
        view(request, response, "member/reset-password");
    }

    private void resetPassword(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String token = param(request, "token");
        String pass = request.getParameter("pass");
        try {
            if (pass == null || !pass.equals(request.getParameter("pass2"))) {
                throw new ValidationException("비밀번호 확인이 일치하지 않습니다.");
            }
            mService.resetPassword(token, pass);
            flash(request, "비밀번호를 바꿨습니다. 새 비밀번호로 로그인하세요.");
            redirect(request, response, "/member?action=login-form");
        } catch (ValidationException e) {
            request.setAttribute("token", token);
            request.setAttribute("valid", mService.isResetTokenValid(token));
            request.setAttribute("error", e.getMessage());
            view(request, response, "member/reset-password");
        }
    }

    private void memberList(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!requireAdmin(request, response)) {
            return;
        }
        SearchCondition cond = new SearchCondition(Set.of("name", "email"), param(request, "key"), param(request, "word"),
                intParam(request, "page", 1), 10);
        request.setAttribute("page", mService.search(cond));
        view(request, response, "member/list");
    }

    private void adminDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireAdmin(request, response)) {
            return;
        }
        long memberId = longParam(request, "memberId", -1);
        if (memberId == loginUser(request).getMemberId()) {
            flash(request, "자기 자신은 여기서 삭제할 수 없습니다.");
        } else {
            mService.delete(memberId);
            flash(request, "회원을 삭제했습니다.");
        }
        redirect(request, response, "/member?action=list");
    }
}
