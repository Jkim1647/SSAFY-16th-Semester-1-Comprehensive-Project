package com.ssafy.enjoytrip.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.model.dto.EvStation;
import com.ssafy.enjoytrip.model.service.EvService;
import com.ssafy.enjoytrip.model.service.EvService.Result;
import com.ssafy.enjoytrip.model.service.ValidationException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** F114 충전소 위치·상태 + F115 충전기 시간대 예약 */
@WebServlet("/ev")
public class EvController extends HttpServlet implements ControllerHelper {

    private static final long serialVersionUID = 1L;
    private static final double DEFAULT_LAT = 35.1468, DEFAULT_LNG = 126.9194;
    private final EvService evService = EvService.getInstance();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        switch (getActionParameter(request, response)) {
            case "index" -> index(request, response);
            case "station" -> station(request, response);
            case "stations-json" -> stationsJson(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        switch (getActionParameter(request, response)) {
            case "reserve" -> reserve(request, response);
            case "cancel" -> cancel(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private double doubleParam(HttpServletRequest request, String name, double defaultValue) {
        try {
            String v = request.getParameter(name);
            return v == null || v.isBlank() ? defaultValue : Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void index(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String q = param(request, "q");
        double lat = doubleParam(request, "lat", DEFAULT_LAT);
        double lng = doubleParam(request, "lng", DEFAULT_LNG);
        int minKw = intParam(request, "minKw", 0);
        List<EvStation> stations = q.isEmpty() ? evService.nearby(lat, lng, 5, minKw, 150) : evService.searchByName(q, 60);
        request.setAttribute("stations", stations);
        request.setAttribute("stationsJson", jsonForScript(stations.stream().map(EvController::marker).toList()));
        request.setAttribute("center", Map.of("lat", lat, "lng", lng));
        view(request, response, "ev/index");
    }

    private void stationsJson(HttpServletRequest request, HttpServletResponse response) throws IOException {
        double lat = doubleParam(request, "lat", DEFAULT_LAT);
        double lng = doubleParam(request, "lng", DEFAULT_LNG);
        double radius = Math.max(0.5, Math.min(doubleParam(request, "radius", 5), 20));
        List<EvStation> stations = evService.nearby(lat, lng, radius, intParam(request, "minKw", 0), 300);
        toJSON(stations.stream().map(EvController::marker).toList(), response);
    }

    private void station(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        EvStation s = evService.findStation(param(request, "statId"));
        if (s == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate date;
        try {
            date = param(request, "date").isEmpty() ? today : LocalDate.parse(param(request, "date"));
        } catch (DateTimeParseException e) {
            date = today;
        }
        if (date.isBefore(today) || date.isAfter(today.plusDays(EvService.BOOKING_DAYS))) {
            date = today;
        }
        request.setAttribute("station", s);
        request.setAttribute("date", date);
        request.setAttribute("dates", today.datesUntil(today.plusDays(EvService.BOOKING_DAYS + 1)).toList());
        request.setAttribute("slots", evService.dayBoard(0, s, date));
        request.setAttribute("stationJson", jsonForScript(marker(s)));
        view(request, response, "ev/station");
    }

    private void reserve(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        String statId = param(request, "statId");
        String back = "/ev?action=station&statId=" + URLEncoder.encode(statId, StandardCharsets.UTF_8);
        try {
            LocalDateTime slot = LocalDateTime.parse(param(request, "slot"));
            back += "&date=" + slot.toLocalDate();
            Result r = evService.reserve(loginUser(request).getMemberId(), statId, slot);
            flash(request, switch (r.outcome()) {
                case CONFIRMED -> String.format("%02d:00 %d번 충전기 예약이 확정되었습니다.", slot.getHour(), r.chargerNo());
                case FULL -> "방금 다른 분이 마지막 자리를 예약했습니다. 다른 시간대를 골라 주세요.";
                case DUPLICATE -> "같은 시간대에 이미 예약이 있습니다.";
            });
        } catch (DateTimeParseException e) {
            flash(request, "예약 시간이 올바르지 않습니다.");
        } catch (ValidationException e) {
            flash(request, e.getMessage());
        }
        redirect(request, response, back);
    }

    private void cancel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        try {
            evService.cancel(loginUser(request).getMemberId(), longParam(request, "reservationId", -1));
            flash(request, "예약을 취소했습니다.");
        } catch (ValidationException e) {
            flash(request, e.getMessage());
        }
        redirect(request, response, "/member?action=mypage");
    }

    private static Map<String, Object> marker(EvStation s) {
        return Map.of("id", s.getStatId(), "name", s.getName(), "lat", s.getLatitude(), "lng", s.getLongitude(),
                "kw", s.getMaxKw(), "chargers", s.getChargerCount(), "dist", s.getDistanceKm() == null ? -1 : s.getDistanceKm());
    }
}
