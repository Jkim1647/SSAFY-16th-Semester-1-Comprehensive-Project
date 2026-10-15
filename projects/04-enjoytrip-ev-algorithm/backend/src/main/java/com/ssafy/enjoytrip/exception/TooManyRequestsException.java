package com.ssafy.enjoytrip.exception;

/** 서버가 처리할 수 있는 양을 넘는 요청 (429). 잠시 뒤 다시 시도하면 된다. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
