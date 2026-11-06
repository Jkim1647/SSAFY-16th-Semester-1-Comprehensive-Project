package com.ssafy.enjoytrip.controller;

import java.io.IOException;
import java.util.Map;

import com.ssafy.enjoytrip.model.service.EvService.Mode;
import com.ssafy.enjoytrip.model.service.ValidationException;
import com.ssafy.enjoytrip.simulation.SimulationEngine;
import com.ssafy.enjoytrip.simulation.SimulationEngine.Config;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 충전 예약 몰림 시뮬레이션: 이야기(인트로) → 실행 → 실시간 상태(AJAX 폴링) */
@WebServlet("/sim")
public class SimulationController extends HttpServlet implements ControllerHelper {

    private static final long serialVersionUID = 1L;
    private final SimulationEngine engine = SimulationEngine.getInstance();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        switch (getActionParameter(request, response)) {
            case "index" -> {
                request.setAttribute("history", engine.history(10));
                var run = engine.current();
                request.setAttribute("activeRunId", run != null && run.isRunning() ? run.getRunId() : 0);
                view(request, response, "sim/index");
            }
            case "status" -> status(request, response);
            case "history" -> toJSON(engine.history(10), response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!"start".equals(getActionParameter(request, response))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            Mode mode = "NAIVE".equals(param(request, "mode")) ? Mode.NAIVE : Mode.LOCKED;
            String queue = param(request, "queue"); // memory | memory-partitioned | kafka
            Config cfg = new Config(intParam(request, "vehicles", 800), intParam(request, "stations", 16), mode,
                    queue, "memory-partitioned".equals(queue), intParam(request, "burstMs", 400), 0);
            toJSON(Map.of("runId", engine.start(cfg)), response);
        } catch (ValidationException e) {
            jsonError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        }
    }

    private void status(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> s = engine.status(longParam(request, "runId", -1), longParam(request, "since", 0),
                "true".equals(request.getParameter("full")));
        if (s == null) {
            jsonError(response, HttpServletResponse.SC_NOT_FOUND, "실행 중인 시뮬레이션이 아닙니다.");
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        toJSON(s, response);
    }
}
