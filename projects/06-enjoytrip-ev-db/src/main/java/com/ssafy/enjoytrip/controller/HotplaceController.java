package com.ssafy.enjoytrip.controller;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ssafy.enjoytrip.model.dto.Hotplace;
import com.ssafy.enjoytrip.model.dto.HotplaceCondition;
import com.ssafy.enjoytrip.model.dto.Page;
import com.ssafy.enjoytrip.model.dto.PhotoUpload;
import com.ssafy.enjoytrip.model.service.HotplaceService;
import com.ssafy.enjoytrip.model.service.ValidationException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

/** F105 회원 주도의 핫플레이스 등록: 목록(지도+카드) · 상세 · 등록/수정/삭제 (사진 업로드 포함) */
@WebServlet("/hotplace")
@MultipartConfig(maxFileSize = 5L * 1024 * 1024, maxRequestSize = 6L * 1024 * 1024)
public class HotplaceController extends HttpServlet implements ControllerHelper {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 12;
    private static final Pattern QUERY_ID = Pattern.compile("(?:^|&)id=(\\d+)");
    private final HotplaceService hService = HotplaceService.getInstance();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        switch (getActionParameter(request, response)) {
            case "index", "list" -> list(request, response);
            case "detail" -> detail(request, response);
            case "regist-form" -> registForm(request, response);
            case "edit-form" -> editForm(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action;
        try {
            action = getActionParameter(request, response);
        } catch (IllegalStateException e) {
            // @MultipartConfig 크기 제한을 넘으면 파라미터를 처음 읽는 순간 컨테이너가 IllegalStateException 을 던진다
            uploadTooLarge(request, response);
            return;
        }
        switch (action) {
            case "regist" -> regist(request, response);
            case "modify" -> modify(request, response);
            case "delete" -> delete(request, response);
            default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void list(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        boolean mine = "1".equals(param(request, "mine"));
        if (mine && !requireLogin(request, response)) {
            return;
        }
        // 유형은 허용 목록에 있는 값만 조건으로 쓴다
        String type = HotplaceService.PLACE_TYPES.contains(param(request, "type")) ? param(request, "type") : null;
        Long memberId = mine ? loginUser(request).getMemberId() : null;

        Page<Hotplace> page = hService.search(new HotplaceCondition(type, memberId, intParam(request, "page", 1), PAGE_SIZE));
        request.setAttribute("page", page);
        request.setAttribute("placeTypes", HotplaceService.PLACE_TYPES);
        request.setAttribute("selectedType", type);
        request.setAttribute("mine", mine);
        List<Hotplace> markers = hService.mapMarkers(type, memberId);
        request.setAttribute("markerCount", markers.size());
        request.setAttribute("markersJson", jsonForScript(markers.stream().map(HotplaceController::marker).toList()));
        view(request, response, "hotplace/list");
    }

    private void detail(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Hotplace h = hService.findById(longParam(request, "id", -1));
        if (h == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setAttribute("hp", h);
        request.setAttribute("canEdit", hService.canEdit(loginUser(request), h));
        request.setAttribute("hpJson", jsonForScript(marker(h)));
        view(request, response, "hotplace/detail");
    }

    private void registForm(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        showForm(request, response, new Hotplace(), "regist", null);
    }

    private void editForm(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        Hotplace h = editableOrError(request, response);
        if (h != null) {
            showForm(request, response, h, "modify", null);
        }
    }

    private void regist(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        Hotplace input = readForm(request);
        try {
            long id = hService.regist(loginUser(request), input, readPhoto(request));
            flash(request, "핫플레이스를 등록했습니다.");
            redirect(request, response, "/hotplace?action=detail&id=" + id);
        } catch (ValidationException e) {
            showForm(request, response, input, "regist", e.getMessage());
        }
    }

    private void modify(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        Hotplace old = editableOrError(request, response);
        if (old == null) {
            return;
        }
        Hotplace input = readForm(request);
        try {
            hService.modify(loginUser(request), old.getHotplaceId(), input, readPhoto(request), "1".equals(param(request, "removePhoto")));
            flash(request, "핫플레이스를 수정했습니다.");
            redirect(request, response, "/hotplace?action=detail&id=" + old.getHotplaceId());
        } catch (ValidationException e) {
            input.setHotplaceId(old.getHotplaceId());
            input.setPhotoFile(old.getPhotoFile());
            showForm(request, response, input, "modify", e.getMessage());
        }
    }

    private void delete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        Hotplace old = editableOrError(request, response);
        if (old == null) {
            return;
        }
        hService.delete(loginUser(request), old.getHotplaceId());
        flash(request, "핫플레이스를 삭제했습니다.");
        redirect(request, response, "/hotplace");
    }

    /**
     * 업로드 크기 초과: 요청 파라미터를 읽을 수 없으므로 쿼리 문자열(파싱 없이 읽힘)에서 id 만 꺼내
     * 원래 폼으로 돌려보내고 안내 메시지를 띄운다.
     */
    private void uploadTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        flash(request, "사진은 5MB 이하만 올릴 수 있습니다.");
        String query = request.getQueryString() == null ? "" : request.getQueryString();
        Matcher m = QUERY_ID.matcher(query);
        redirect(request, response, query.contains("action=modify") && m.find()
                ? "/hotplace?action=edit-form&id=" + m.group(1)
                : "/hotplace?action=regist-form");
    }

    /** id 의 핫플레이스를 찾고, 작성자나 관리자가 아니면 403 을 보낸 뒤 null 을 돌려준다. */
    private Hotplace editableOrError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Hotplace h = hService.findById(longParam(request, "id", -1));
        if (h == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!hService.canEdit(loginUser(request), h)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return h;
    }

    private void showForm(HttpServletRequest request, HttpServletResponse response, Hotplace h, String mode, String error)
            throws ServletException, IOException {
        request.setAttribute("hp", h);
        request.setAttribute("mode", mode);
        request.setAttribute("error", error);
        request.setAttribute("placeTypes", HotplaceService.PLACE_TYPES);
        request.setAttribute("today", HotplaceService.today());
        view(request, response, "hotplace/form");
    }

    /** 폼 값을 DTO 로 옮긴다. 형식이 잘못된 날짜·좌표는 null 로 두고, 규칙 검사는 서비스가 한다. */
    private Hotplace readForm(HttpServletRequest request) {
        Hotplace h = new Hotplace();
        h.setTitle(param(request, "title"));
        h.setPlaceType(param(request, "placeType"));
        h.setDescription(param(request, "description"));
        try {
            h.setVisitedOn(LocalDate.parse(param(request, "visitedOn")));
        } catch (DateTimeParseException e) {
            h.setVisitedOn(null);
        }
        h.setLatitude(doubleParam(request, "latitude"));
        h.setLongitude(doubleParam(request, "longitude"));
        return h;
    }

    private Double doubleParam(HttpServletRequest request, String name) {
        try {
            double v = Double.parseDouble(param(request, name));
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 사진 파트를 읽는다. 클라이언트 파일명(part.getSubmittedFileName())은 쓰지 않는다. 없으면 null. */
    private PhotoUpload readPhoto(HttpServletRequest request) throws IOException, ServletException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
            return null;
        }
        Part part = request.getPart("photo");
        if (part == null || part.getSize() == 0) {
            return null;
        }
        try (InputStream in = part.getInputStream()) {
            // 최대 크기 + 1 바이트까지만 읽어 크기 초과를 서비스가 판단할 수 있게 한다
            return new PhotoUpload(part.getContentType(), in.readNBytes((int) HotplaceService.MAX_PHOTO_BYTES + 1));
        } finally {
            part.delete();
        }
    }

    private static Map<String, Object> marker(Hotplace h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getHotplaceId());
        m.put("title", h.getTitle());
        m.put("type", h.getPlaceType());
        m.put("lat", h.getLatitude());
        m.put("lng", h.getLongitude());
        m.put("visitedOn", String.valueOf(h.getVisitedOn()));
        m.put("photo", h.getPhotoFile() == null ? "" : h.getPhotoFile());
        return m;
    }
}
