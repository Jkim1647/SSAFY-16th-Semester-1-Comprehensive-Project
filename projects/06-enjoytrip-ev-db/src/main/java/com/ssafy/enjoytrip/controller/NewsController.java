package com.ssafy.enjoytrip.controller;

import java.io.IOException;
import java.util.List;

import com.ssafy.enjoytrip.model.service.NewsService;
import com.ssafy.enjoytrip.model.service.NewsService.CrawlResult;
import com.ssafy.enjoytrip.model.service.ValidationException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** F106 관광지 관련 뉴스: 누구나 목록을 보고, 관리자는 검색어를 넣어 지금 수집할 수 있다. */
@WebServlet("/news")
public class NewsController extends HttpServlet implements ControllerHelper {

    private static final long serialVersionUID = 1L;
    private static final String RESULT_ATTR = "crawlResults";

    private final NewsService nService = NewsService.getInstance();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        switch (getActionParameter(request, response)) {
            case "index" -> list(request, response);
            case "admin" -> admin(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        switch (getActionParameter(request, response)) {
            case "crawl" -> crawl(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void list(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String keyword = param(request, "keyword");
        request.setAttribute("keyword", keyword);
        request.setAttribute("keywords", nService.keywordCounts());
        request.setAttribute("page", nService.search(keyword, intParam(request, "page", 1), 15));
        view(request, response, "news/list");
    }

    private void admin(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!requireAdmin(request, response)) {
            return;
        }
        // 직전 수집 결과는 한 번만 보여준다 (POST → redirect → GET)
        var session = request.getSession();
        request.setAttribute("results", session.getAttribute(RESULT_ATTR));
        session.removeAttribute(RESULT_ATTR);
        request.setAttribute("defaultKeywords", String.join("\n", NewsService.DEFAULT_KEYWORDS));
        request.setAttribute("keywords", nService.keywordCounts());
        view(request, response, "news/admin");
    }

    private void crawl(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireAdmin(request, response)) {
            return;
        }
        try {
            List<CrawlResult> results = nService.crawl(nService.parseKeywords(request.getParameter("keywords")));
            int saved = results.stream().mapToInt(CrawlResult::getSaved).sum();
            long failed = results.stream().filter(r -> !r.isSuccess()).count();
            request.getSession().setAttribute(RESULT_ATTR, results);
            flash(request, "뉴스 수집 완료: 새 기사 " + saved + "건" + (failed > 0 ? " (실패한 검색어 " + failed + "개)" : ""));
        } catch (ValidationException e) {
            flash(request, e.getMessage());
        }
        redirect(request, response, "/news?action=admin");
    }
}
