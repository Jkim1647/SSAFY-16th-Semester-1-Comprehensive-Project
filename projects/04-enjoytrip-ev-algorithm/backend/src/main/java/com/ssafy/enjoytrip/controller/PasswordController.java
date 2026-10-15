package com.ssafy.enjoytrip.controller;

import com.ssafy.enjoytrip.model.dto.PasswordDtos.HashResult;
import com.ssafy.enjoytrip.model.dto.PasswordDtos.VerifyResult;
import com.ssafy.enjoytrip.model.service.PasswordService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * #3 비밀번호 해시 시연 API. 저장값을 클라이언트가 보내는 구조라 시연용이며,
 * 회원 기능이 생기면 회원가입·로그인 API 안에서 PasswordService 를 직접 쓰고 이 컨트롤러는 지운다.
 */
@RestController
@RequestMapping("/api/password")
public class PasswordController {

    private final PasswordService service;

    public PasswordController(PasswordService service) {
        this.service = service;
    }

    @PostMapping("/hash")
    public HashResult hash(@RequestParam String password) {
        return service.hash(password);
    }

    @PostMapping("/verify")
    public VerifyResult verify(@RequestParam String password, @RequestParam String stored) {
        return service.verify(password, stored);
    }
}
