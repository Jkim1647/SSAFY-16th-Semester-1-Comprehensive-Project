package com.ssafy.enjoytrip.controller;

import java.io.IOException;

import com.ssafy.enjoytrip.model.dto.AttractionCondition;
import com.ssafy.enjoytrip.model.service.AttractionService;
import com.ssafy.enjoytrip.simulation.SimulationEngine;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 메인 화면. 빈 문자열 매핑 = 컨텍스트 루트("/") 정확히 일치. */
@WebServlet("")
public class MainController extends HttpServlet implements ControllerHelper {

    private static final long serialVersionUID = 1L;
    private final AttractionService aService = AttractionService.getInstance();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("contentTypes", aService.contentTypes());
        request.setAttribute("attractionCount", aService.search(new AttractionCondition(null, null, null, 1, 1)).getTotalItems());
        request.setAttribute("runs", SimulationEngine.getInstance().history(3));
        view(request, response, "index");
    }
}
