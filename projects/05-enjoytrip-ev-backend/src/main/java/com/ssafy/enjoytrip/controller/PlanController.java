package com.ssafy.enjoytrip.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlan;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.route.RouteResult;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlan;
import com.ssafy.enjoytrip.model.dto.Attraction;
import com.ssafy.enjoytrip.model.dto.Member;
import com.ssafy.enjoytrip.model.dto.Plan;
import com.ssafy.enjoytrip.model.dto.PlanStop;
import com.ssafy.enjoytrip.model.service.AttractionService;
import com.ssafy.enjoytrip.model.service.PlanService;
import com.ssafy.enjoytrip.model.service.ValidationException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/** F104 여행 계획: 내 계획 목록 · 작성/수정 · 상세 · 방문 순서 최적화 · 충전 경로 계산. 모든 기능은 로그인 후 사용한다. */
@WebServlet("/plan")
public class PlanController extends HttpServlet implements ControllerHelper {

    private static final long serialVersionUID = 1L;
    private final PlanService pService = PlanService.getInstance();
    private final AttractionService aService = AttractionService.getInstance();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        switch (getActionParameter(request, response)) {
            case "index", "list" -> list(request, response);
            case "create-form" -> createForm(request, response);
            case "edit-form" -> editForm(request, response);
            case "detail" -> detail(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        switch (getActionParameter(request, response)) {
            case "save" -> save(request, response);
            case "delete" -> delete(request, response);
            case "optimize" -> optimize(request, response);
            case "ev-trip" -> evTrip(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void list(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("plans", pService.myPlans(loginUser(request).getMemberId()));
        view(request, response, "plan/list");
    }

    /** 새 계획. 관광지 상세의 "여행 계획에 담기"로 오면 contentId 관광지를 첫 방문지로 넣어 둔다. */
    private void createForm(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Plan plan = new Plan();
        Attraction a = aService.findById(intParam(request, "contentId", -1));
        if (a != null) {
            PlanStop s = new PlanStop(a.getContentId(), null, 0, null);
            s.setTitle(a.getTitle());
            s.setLatitude(a.getLatitude());
            s.setLongitude(a.getLongitude());
            s.setContentTypeName(a.getContentTypeName());
            s.setAddr1(a.getAddr1());
            plan.getStops().add(s);
        }
        editor(request, response, plan);
    }

    private void editForm(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Plan plan = pService.findMine(loginUser(request).getMemberId(), longParam(request, "planId", -1));
        if (plan == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        editor(request, response, plan);
    }

    private void editor(HttpServletRequest request, HttpServletResponse response, Plan plan) throws ServletException, IOException {
        request.setAttribute("plan", plan);
        request.setAttribute("planJson", jsonForScript(planJson(plan)));
        request.setAttribute("contentTypes", aService.contentTypes());
        request.setAttribute("maxStops", PlanService.MAX_STOPS);
        view(request, response, "plan/editor");
    }

    private void detail(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Plan plan = pService.findMine(loginUser(request).getMemberId(), longParam(request, "planId", -1));
        if (plan == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Map<String, Object> trip = null;
        try {
            EvTripPlan ev = pService.evTrip(plan);
            if (ev != null) {
                trip = tripJson(ev, true);
                request.setAttribute("trip", trip);
            }
        } catch (ValidationException e) {
            request.setAttribute("tripError", e.getMessage());
        }
        request.setAttribute("plan", plan);
        request.setAttribute("planJson", jsonForScript(planJson(plan)));
        request.setAttribute("tripJson", jsonForScript(trip));
        view(request, response, "plan/detail");
    }

    // ---------------------------------------------------------------- POST

    /** 편집 화면이 계획 전체를 JSON 문자열(planJson 필드)이나 JSON 본문으로 보낸다. */
    private void save(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Plan plan = parsePlan(readPlanJson(request));
            long planId = pService.save(loginUser(request).getMemberId(), plan);
            flash(request, "여행 계획을 저장했습니다.");
            toJSON(Map.of("planId", planId), response);
        } catch (ValidationException e) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    private void delete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Member user = loginUser(request);
        try {
            pService.delete(user.getMemberId(), longParam(request, "planId", -1));
            if (isAjax(request)) {
                toJSON(Map.of("ok", true), response);
                return;
            }
            flash(request, "여행 계획을 삭제했습니다.");
        } catch (ValidationException e) {
            if (isAjax(request)) {
                jsonError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
                return;
            }
            flash(request, e.getMessage());
        }
        redirect(request, response, "/plan");
    }

    /** 방문 순서 최적화 (AJAX). contentIds = "1,2,3" (첫 번째가 출발지). */
    private void optimize(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            RouteResult r = pService.optimize(idList(request), "true".equals(param(request, "returnToStart")));
            List<Integer> order = new ArrayList<>();
            order.add(Integer.valueOf(r.getStart().getId()));
            r.getOrderedStops().forEach(l -> order.add(Integer.valueOf(l.getId())));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("order", order);
            result.put("totalKm", round1(r.getTotalKm()));
            result.put("method", r.getMethod().name());
            result.put("methodLabel", methodLabel(r.getMethod()));
            toJSON(result, response);
        } catch (ValidationException e) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    /** 충전 경로 계산 (AJAX). contentIds, startSoc(%), returnToStart, optimize(true 면 순서도 최적화). */
    private void evTrip(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            boolean optimizeOrder = "true".equals(param(request, "optimize"));
            EvTripPlan ev = pService.evTrip(idList(request), intParam(request, "startSoc", -1),
                    "true".equals(param(request, "returnToStart")), optimizeOrder);
            toJSON(tripJson(ev, !optimizeOrder), response);
        } catch (ValidationException e) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 요청 해석

    private List<Integer> idList(HttpServletRequest request) {
        List<Integer> ids = new ArrayList<>();
        for (String s : param(request, "contentIds").split(",")) {
            if (s.isBlank()) {
                continue;
            }
            try {
                ids.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
                throw new ValidationException("관광지 번호가 올바르지 않습니다.");
            }
        }
        return ids;
    }

    private String readPlanJson(HttpServletRequest request) throws IOException {
        String type = request.getContentType();
        if (type != null && type.startsWith("application/json")) {
            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        return request.getParameter("planJson");
    }

    /** { planId, title, startDate, endDate, startSoc, memo, stops: [{ contentId, visitDate, cost, memo }] } */
    private Plan parsePlan(String json) {
        if (json == null || json.isBlank()) {
            throw new ValidationException("저장할 계획 내용이 없습니다.");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new ValidationException("계획 데이터 형식이 올바르지 않습니다.");
        }
        if (!root.isObject()) {
            throw new ValidationException("계획 데이터 형식이 올바르지 않습니다.");
        }
        Plan plan = new Plan();
        plan.setPlanId(Math.max(0, number(root, "planId", 0, "계획 번호")));
        plan.setTitle(text(root, "title"));
        plan.setStartDate(date(root, "startDate"));
        plan.setEndDate(date(root, "endDate"));
        plan.setStartSoc((int) Math.max(-1, Math.min(1000, number(root, "startSoc", 80, "출발 배터리"))));
        plan.setMemo(text(root, "memo"));
        JsonNode stops = root.path("stops");
        if (!stops.isMissingNode() && !stops.isNull() && !stops.isArray()) {
            throw new ValidationException("계획 데이터 형식이 올바르지 않습니다.");
        }
        for (JsonNode s : stops) {
            int contentId = (int) number(s, "contentId", -1, "관광지 번호");
            int cost = (int) Math.max(-1, Math.min(Integer.MAX_VALUE, number(s, "cost", 0, "경비"))); // 범위 검사는 서비스에서
            plan.getStops().add(new PlanStop(contentId, date(s, "visitDate"), cost, text(s, "memo")));
        }
        return plan;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asString("");
    }

    private static long number(JsonNode node, String field, long defaultValue, String label) {
        String v = text(node, field);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(label + "은(는) 정수로 입력하세요.");
        }
    }

    private static LocalDate date(JsonNode node, String field) {
        String v = text(node, field);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException("날짜 형식이 올바르지 않습니다: " + v);
        }
    }

    // ---------------------------------------------------------------- 응답 JSON

    /** 편집기·상세 지도에서 쓰는 계획 데이터. 방문지 키 이름은 /attraction?action=search-json 과 맞췄다. */
    private static Map<String, Object> planJson(Plan plan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planId", plan.getPlanId());
        m.put("title", plan.getTitle() == null ? "" : plan.getTitle());
        m.put("startDate", plan.getStartDate() == null ? "" : plan.getStartDate().toString());
        m.put("endDate", plan.getEndDate() == null ? "" : plan.getEndDate().toString());
        m.put("startSoc", plan.getStartSoc());
        m.put("memo", plan.getMemo() == null ? "" : plan.getMemo());
        List<Map<String, Object>> stops = new ArrayList<>();
        for (PlanStop s : plan.getStops()) {
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("id", s.getContentId());
            st.put("title", s.getTitle());
            st.put("lat", s.getLatitude());
            st.put("lng", s.getLongitude());
            st.put("type", s.getContentTypeName() == null ? "" : s.getContentTypeName());
            st.put("addr", s.getAddr1() == null ? "" : s.getAddr1());
            st.put("visitDate", s.getVisitDate() == null ? "" : s.getVisitDate().toString());
            st.put("cost", s.getCost());
            st.put("memo", s.getMemo() == null ? "" : s.getMemo());
            stops.add(st);
        }
        m.put("stops", stops);
        return m;
    }

    /** 충전 계획을 지도에 그리기 좋은 형태로. 불가능한 구간의 수치는 NaN 이라 null 로 바꾼다. */
    private static Map<String, Object> tripJson(EvTripPlan ev, boolean keptOrder) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean feasible = ev.isFeasible();
        m.put("feasible", feasible);
        m.put("method", keptOrder ? "USER_ORDER" : ev.getRoute().getMethod().name());
        m.put("methodLabel", keptOrder ? "내가 정한 순서" : methodLabel(ev.getRoute().getMethod()));
        List<Integer> order = new ArrayList<>();
        order.add(Integer.valueOf(ev.getRoute().getStart().getId()));
        ev.getRoute().getOrderedStops().forEach(l -> order.add(Integer.valueOf(l.getId())));
        m.put("order", order);
        m.put("returnToStart", ev.getRoute().isReturnToStart());
        m.put("totalKm", feasible ? round1(ev.getTotalKm()) : null);
        m.put("totalMin", feasible ? Math.round(ev.getTotalMin()) : null);
        m.put("totalTimeLabel", feasible ? timeLabel(Math.round(ev.getTotalMin())) : null);
        m.put("chargeStopCount", ev.getChargeStopCount());
        List<Map<String, Object>> legs = new ArrayList<>();
        for (EvRoutePlan leg : ev.getLegs()) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("from", point(leg.getOrigin()));
            l.put("to", point(leg.getDestination()));
            l.put("feasible", leg.isFeasible());
            l.put("km", leg.isFeasible() ? round1(leg.getTotalKm()) : null);
            l.put("driveMin", leg.isFeasible() ? Math.round(leg.getDriveMin()) : null);
            l.put("chargeMin", leg.isFeasible() ? Math.round(leg.getChargeMin()) : null);
            l.put("arrivalSoc", leg.isFeasible() ? Math.round(leg.getArrivalSoc()) : null);
            List<Map<String, Object>> stops = new ArrayList<>();
            for (EvRoutePlan.ChargeStop cs : leg.getStops()) {
                Map<String, Object> c = point(cs.getStation());
                c.put("maxKw", Math.round(cs.getStation().getMaxKw()));
                c.put("arriveSoc", Math.round(cs.getArriveSoc()));
                c.put("departSoc", Math.round(cs.getDepartSoc()));
                c.put("chargeMin", Math.round(cs.getChargeMin()));
                stops.add(c);
            }
            l.put("chargeStops", stops);
            legs.add(l);
        }
        m.put("legs", legs);
        // 앞 구간이 불가능하면 EvTripPlanner 가 거기서 멈추므로, 계산하지 못한 구간 수를 알려 준다
        int expectedLegs = order.size() - 1 + (ev.getRoute().isReturnToStart() ? 1 : 0);
        m.put("skippedLegs", Math.max(0, expectedLegs - legs.size()));
        return m;
    }

    private static Map<String, Object> point(Location p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("lat", p.getLat());
        m.put("lng", p.getLng());
        return m;
    }

    private static String methodLabel(RouteResult.Method method) {
        return switch (method) {
            case TRIVIAL -> "계산 불필요 (2곳 이하)";
            case HELD_KARP -> "Held-Karp 동적 계획법 (최적해)";
            case NEAREST_NEIGHBOR_2OPT -> "최근접 이웃 + 2-opt (근사해)";
        };
    }

    private static String timeLabel(long minutes) {
        return minutes >= 60 ? (minutes / 60) + "시간 " + (minutes % 60) + "분" : minutes + "분";
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
