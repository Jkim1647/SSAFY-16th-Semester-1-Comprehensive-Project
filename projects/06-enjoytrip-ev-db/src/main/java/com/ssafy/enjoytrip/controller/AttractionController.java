package com.ssafy.enjoytrip.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.model.dto.Attraction;
import com.ssafy.enjoytrip.model.dto.AttractionCondition;
import com.ssafy.enjoytrip.model.dto.Page;
import com.ssafy.enjoytrip.model.service.AttractionService;
import com.ssafy.enjoytrip.model.service.EvService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** F101 지역별 관광지 · F102 관광지/숙박/음식점 · F103 문화시설/공연/여행코스/쇼핑 조회 (+ F111~F113 상세) */
@WebServlet("/attraction")
public class AttractionController extends HttpServlet implements ControllerHelper {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 12;
    private final AttractionService aService = AttractionService.getInstance();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        switch (getActionParameter(request, response)) {
            case "index", "list" -> list(request, response);
            case "detail" -> detail(request, response);
            case "search-json" -> searchJson(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private AttractionCondition condition(HttpServletRequest request, int size) {
        return new AttractionCondition(intParam(request, "signgu", 0), intParam(request, "type", 0),
                param(request, "keyword"), intParam(request, "page", 1), size);
    }

    private void list(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Page<Attraction> page = aService.search(condition(request, PAGE_SIZE));
        request.setAttribute("page", page);
        request.setAttribute("signguList", aService.signguList());
        request.setAttribute("contentTypes", aService.contentTypes());
        request.setAttribute("spotsJson", jsonForScript(page.getList().stream().map(AttractionController::marker).toList()));
        view(request, response, "attraction/list");
    }

    private void detail(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Attraction a = aService.findById(intParam(request, "contentId", -1));
        if (a == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setAttribute("spot", a);
        request.setAttribute("stations", EvService.getInstance().nearby(a.getLatitude(), a.getLongitude(), 10, 0, 8));
        request.setAttribute("spotJson", jsonForScript(marker(a)));
        view(request, response, "attraction/detail");
    }

    /** 여행 계획 화면의 관광지 검색 (AJAX). */
    private void searchJson(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Page<Attraction> page = aService.search(condition(request, 20));
        List<Map<String, Object>> items = page.getList().stream().map(AttractionController::marker).toList();
        toJSON(Map.of("total", page.getTotalItems(), "items", items,
                "page", page.getCondition().getCurrentPage(), "totalPages", page.getTotalPages()), response);
    }

    private static Map<String, Object> marker(Attraction a) {
        return Map.of("id", a.getContentId(), "title", a.getTitle(), "lat", a.getLatitude(), "lng", a.getLongitude(),
                "type", a.getContentTypeName(), "addr", a.getAddr1() == null ? "" : a.getAddr1(),
                "image", a.getFirstImage2() == null ? "" : a.getFirstImage2());
    }
}
