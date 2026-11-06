package com.ssafy.enjoytrip.controller;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import com.ssafy.enjoytrip.model.dto.*;
import com.ssafy.enjoytrip.model.service.*;
import com.ssafy.enjoytrip.model.service.BoardCommunityService.Problem;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

/** Unified community; legacy type=free/notice URLs remain filters. */
@WebServlet("/board")
public class BoardController extends HttpServlet implements ControllerHelper {
    private static final long serialVersionUID = 1L;
    private final BoardCommunityService community = BoardCommunityService.getInstance();
    private final BoardService boards = BoardService.getInstance();
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        res.setHeader("X-Request-ID", requestId);
        String action = getActionParameter(req, res).replaceAll("[^a-zA-Z0-9_-]", "_");
        action = action.substring(0, Math.min(action.length(), 40));
        try {
            super.service(req, res);
        } catch (RuntimeException | ServletException e) {
            if (!res.isCommitted())
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            getServletContext().log("board request=" + requestId + " action=" + action + " failed", e);
            throw e;
        } finally {
            getServletContext().log("board request=" + requestId + " method=" + req.getMethod() + " action=" + action
                    + " board=" + longParam(req, "id", 0) + " status=" + res.getStatus() + " durationMs="
                    + ((System.nanoTime() - start) / 1_000_000));
        }
    }
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");
        try {
            String action = getActionParameter(req, res);
            if (Set.of("index", "write-form").contains(action)
                    && !Set.of("", "free", "notice").contains(param(req, "type")))
                throw new Problem(404, "지원하지 않는 게시판 유형입니다.");
            BoardQuery q = query(req);
            attributes(req, q);
            switch (getActionParameter(req, res)) {
                case "index" -> list(req, res, q);
                case "detail" -> detail(req, res, q);
                case "write-form" -> {
                    if (!requireLogin(req, res))
                        return;
                    if (q.getType().equals("notice") && !loginUser(req).isAdmin())
                        throw new Problem(403, "공지사항은 관리자만 쓸 수 있습니다.");
                    form(req, res, q, null);
                }
                case "edit-form" -> {
                    if (!requireLogin(req, res))
                        return;
                    BoardEntry b = community.get(id(req), loginUser(req));
                    if (!boards.canManage(b, loginUser(req)))
                        throw new Problem(403, "수정 권한이 없습니다.");
                    form(req, res, q, b);
                }
                case "reports" -> {
                    if (!requireAdmin(req, res))
                        return;
                    req.setAttribute("page", community.reports(loginUser(req), intParam(req, "page", 1)));
                    view(req, res, "board/reports");
                }
                default -> res.sendError(404);
            }
        } catch (Problem e) {
            error(req, res, e);
        }
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");
        if (!requireLogin(req, res))
            return;
        BoardQuery q = query(req);
        Member user = loginUser(req);
        String action = getActionParameter(req, res);
        BoardEntry editing = null;
        try {
            long boardId;
            switch (action) {
                case "write" -> {
                    String type = param(req, "boardType");
                    if (type.isEmpty())
                        type = "notice".equals(param(req, "type")) ? "NOTICE" : "FREE";
                    boardId = community.write(user, type, req.getParameter("title"), req.getParameter("content"),
                            postCategory(req));
                    redirect(req, res, detailUrl(boardId, q));
                    return;
                }
                case "modify" -> {
                    boardId = id(req);
                    editing = community.get(boardId, user);
                    if (!boards.canManage(editing, user))
                        throw new Problem(403, "수정 권한이 없습니다.");
                    community.modify(user, boardId, req.getParameter("title"), req.getParameter("content"),
                            postCategory(req));
                    redirect(req, res, detailUrl(boardId, q));
                    return;
                }
                case "delete" -> {
                    community.delete(user, id(req));
                    flash(req, "글을 삭제했습니다.");
                    redirect(req, res, listUrl(q));
                    return;
                }
                case "reaction" -> {
                    String active = param(req, "active");
                    if (!Set.of("true", "false").contains(active))
                        throw new Problem(400, "반응 상태가 올바르지 않습니다.");
                    toJSON(community.react(user, id(req), param(req, "kind"), Boolean.parseBoolean(active)), res);
                    return;
                }
                case "comment-add" -> {
                    boardId = id(req);
                    long cid = community.addComment(user, boardId, req.getParameter("content"));
                    flash(req, "댓글을 등록했습니다.");
                    redirect(req, res, detailUrl(boardId, q) + "#comment-" + cid);
                    return;
                }
                case "comment-edit" ->
                    community.editComment(user, id(req), requiredLong(req, "commentId"), req.getParameter("content"));
                case "comment-delete" -> community.deleteComment(user, id(req), requiredLong(req, "commentId"));
                case "report" -> community.report(user, id(req), requiredLong(req, "commentId"), req.getParameter("reason"));
                case "moderate" -> community.moderate(user, id(req), optionalCommentId(req),
                        booleanParam(req, "hidden"), req.getParameter("reason"));
                case "pin" -> community.pin(user, id(req), booleanParam(req, "pinned"), intParam(req, "pinOrder", -1),
                        date(req, "pinStart"), date(req, "pinEnd"));
                case "resolve-report" -> {
                    community.resolveReport(user, requiredLong(req, "reportId"), req.getParameter("reason"));
                    flash(req, "신고 처리를 기록했습니다.");
                    redirect(req, res, "/board?action=reports&page=" + Math.max(1, intParam(req, "page", 1)));
                    return;
                }
                default -> {
                    res.sendError(404);
                    return;
                }
            }
            flash(req, action.equals("report") ? "신고를 접수했습니다. 같은 대상의 중복 신고는 한 건으로 처리합니다." : "변경 내용을 저장했습니다.");
            redirect(req, res, detailUrl(id(req), q) + "#comments-title");
        } catch (Problem e) {
            if (e.getStatus() == 400 && (action.equals("write") || (action.equals("modify") && editing != null))) {
                attributes(req, q);
                BoardEntry b = new BoardEntry();
                b.setBoardId(editing == null ? 0 : editing.getBoardId());
                if (b.getBoardId() > 0)
                    b.setBoardType(editing.getBoardType());
                else
                    b.setBoardType("NOTICE".equals(param(req, "boardType")) ? "NOTICE" : "FREE");
                b.setTitle(req.getParameter("title"));
                b.setContent(req.getParameter("content"));
                b.setCategory(postCategory(req));
                req.setAttribute("error", e.getMessage());
                form(req, res, q, b);
                return;
            }
            error(req, res, e);
        }
    }
    private void list(HttpServletRequest req, HttpServletResponse res, BoardQuery q)
            throws ServletException, IOException {
        if (!q.getActivity().isEmpty() && !requireLogin(req, res))
            return;
        req.setAttribute("activityComments", q.getActivity().equals("comments"));
        req.setAttribute("page",
                q.getActivity().equals("comments")
                        ? community.myComments(loginUser(req), q.getCurrentPage())
                        : community.search(loginUser(req), q));
        req.setAttribute("pinned", community.pinned(loginUser(req), q));
        view(req, res, "board/list");
    }
    private void detail(HttpServletRequest req, HttpServletResponse res, BoardQuery q)
            throws ServletException, IOException {
        BoardEntry b = community.get(id(req), loginUser(req));
        if (markViewed(req.getSession(), b.getBoardId())) {
            boards.increaseViewCount(b.getBoardId());
            b.setViewCount(b.getViewCount() + 1);
        }
        req.setAttribute("board", b);
        req.setAttribute("canManage", boards.canManage(b, loginUser(req)));
        req.setAttribute("comments",
                community.allComments(b.getBoardId(), loginUser(req)));
        if (loginUser(req) != null && loginUser(req).isAdmin())
            req.setAttribute("history", community.history(loginUser(req), b.getBoardId()));
        view(req, res, "board/detail");
    }
    @SuppressWarnings("unchecked")
    private boolean markViewed(HttpSession session, long id) {
        synchronized (session) {
            Set<Long> viewed = (Set<Long>) session.getAttribute("viewedBoardIds");
            if (viewed == null)
                viewed = new HashSet<>();
            boolean first = viewed.add(id);
            session.setAttribute("viewedBoardIds", viewed);
            return first;
        }
    }
    private void form(HttpServletRequest req, HttpServletResponse res, BoardQuery q, BoardEntry b)
            throws ServletException, IOException {
        if (b == null) {
            b = new BoardEntry();
            b.setBoardType(q.getType().equals("notice") ? "NOTICE" : "FREE");
            b.setTitle("");
            b.setContent("");
        }
        req.setAttribute("board", b);
        req.setAttribute("titleMax", BoardService.TITLE_MAX);
        req.setAttribute("contentMax", BoardService.CONTENT_MAX);
        view(req, res, "board/form");
    }
    private void attributes(HttpServletRequest req, BoardQuery q) {
        req.setAttribute("query", q);
        req.setAttribute("state", q.getQueryString());
        req.setAttribute("boardLabel", "게시판");
        req.setAttribute("type", "board");
        req.setAttribute("categories",
                List.of(Map.of("value", "GENERAL", "label", "일반"), Map.of("value", "REVIEW", "label", "여행 후기"),
                        Map.of("value", "TIP", "label", "충전 팁"), Map.of("value", "QUESTION", "label", "질문"),
                        Map.of("value", "COMPANION", "label", "동행 모집")));
    }
    private BoardQuery query(HttpServletRequest req) {
        Map<String, String> values = new HashMap<>();
        String state = req.getParameter("state");
        if ("POST".equals(req.getMethod()) && state != null && state.length() <= 2000) {
            try {
                for (String item : state.split("&")) {
                    String[] pair = item.split("=", 2);
                    if (pair.length == 2)
                        values.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                }
            } catch (IllegalArgumentException ignored) {
                values.clear();
            }
        } else
            for (String key : List.of("type", "category", "sort", "activity", "key", "word", "page"))
                values.put(key, param(req, key));
        int page = 1;
        try {
            page = Integer.parseInt(values.getOrDefault("page", "1"));
        } catch (NumberFormatException ignored) {
        }
        return new BoardQuery(values.get("type"), values.get("category"), values.get("sort"), values.get("activity"),
                values.get("key"), values.get("word"), page);
    }
    private static String listUrl(BoardQuery q) {
        return "/board?" + q.getQueryString();
    }
    private static String detailUrl(long id, BoardQuery q) {
        return "/board?action=detail&id=" + id + "&" + q.getQueryString();
    }
    private long id(HttpServletRequest req) {
        return requiredLong(req, "id");
    }
    private long requiredLong(HttpServletRequest req, String key) {
        long value = longParam(req, key, -1);
        if (value <= 0)
            throw new Problem(400, "올바른 대상 ID가 필요합니다.");
        return value;
    }
    private long optionalCommentId(HttpServletRequest req) {
        if (param(req, "commentId").isEmpty())
            return 0;
        return requiredLong(req, "commentId");
    }
    private boolean booleanParam(HttpServletRequest req, String key) {
        String v = param(req, key);
        if (!Set.of("true", "false").contains(v))
            throw new Problem(400, "잘못된 상태 값입니다.");
        return Boolean.parseBoolean(v);
    }
    private String postCategory(HttpServletRequest req) {
        String v = param(req, "postCategory");
        return v.isEmpty() ? "GENERAL" : v;
    }
    private LocalDateTime date(HttpServletRequest req, String key) {
        String v = param(req, key);
        if (v.isEmpty())
            return null;
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException e) {
            throw new Problem(400, "올바른 날짜와 시간을 입력하세요.");
        }
    }
    private void error(HttpServletRequest req, HttpServletResponse res, Problem e) throws IOException {
        if (isAjax(req))
            jsonError(res, e.getStatus(), e.getMessage());
        else
            res.sendError(e.getStatus(), e.getMessage());
    }
}
