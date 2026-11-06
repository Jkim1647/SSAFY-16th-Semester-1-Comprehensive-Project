package com.ssafy.enjoytrip.util;

/** SQLException 을 서비스·컨트롤러까지 checked 로 끌고 가지 않도록 감싸는 런타임 예외. */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
