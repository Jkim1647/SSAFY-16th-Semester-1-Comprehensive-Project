package com.ssafy.enjoytrip.controller;

import com.ssafy.enjoytrip.model.dto.AttractionResponses.Meta;
import com.ssafy.enjoytrip.model.service.AttractionService;
import com.ssafy.enjoytrip.model.service.PasswordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면 초기화용 정보 (분류 목록, 데이터 수, 비밀번호 설정) */
@RestController
public class MetaController {

    private final AttractionService attractionService;
    private final PasswordService passwordService;

    public MetaController(AttractionService attractionService, PasswordService passwordService) {
        this.attractionService = attractionService;
        this.passwordService = passwordService;
    }

    @GetMapping("/api/meta")
    public Meta meta() {
        return new Meta(attractionService.count(), attractionService.categories(),
                passwordService.iterations(), passwordService.isDemoPepper());
    }
}
