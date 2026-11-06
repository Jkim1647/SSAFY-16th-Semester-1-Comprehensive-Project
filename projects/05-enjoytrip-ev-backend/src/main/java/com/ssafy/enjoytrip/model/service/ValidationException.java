package com.ssafy.enjoytrip.model.service;

/** 사용자 입력이 규칙에 맞지 않을 때. 메시지는 화면에 그대로 보여줘도 되는 문장으로 쓴다. */
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
