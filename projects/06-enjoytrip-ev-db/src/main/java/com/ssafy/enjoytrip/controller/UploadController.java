package com.ssafy.enjoytrip.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.ssafy.enjoytrip.util.PhotoStorage;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 업로드 폴더(웹앱 밖)의 사진을 내려준다. GET 만 처리한다.
 * /uploads/{UUID}.{jpg|png|webp|gif} 형식이 아닌 이름은 전부 404 로 막아 경로 조작(../)을 원천 차단한다.
 */
@WebServlet("/uploads/*")
public class UploadController extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        String name = pathInfo == null || pathInfo.length() < 2 ? null : pathInfo.substring(1);
        Path file = PhotoStorage.resolve(name);
        if (file == null || !Files.isRegularFile(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(PhotoStorage.contentType(name));
        response.setContentLengthLong(Files.size(file));
        // 브라우저가 내용을 보고 HTML/스크립트로 추측 실행하지 않게 한다
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'");
        // 파일명이 UUID 라 내용이 바뀌지 않는다 → 하루 동안 캐시
        response.setHeader("Cache-Control", "public, max-age=86400");
        Files.copy(file, response.getOutputStream());
    }
}
