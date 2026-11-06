package com.ssafy.enjoytrip.model.dto;

/**
 * 업로드된 사진 한 장. contentType 은 브라우저가 선언한 값이라 믿지 않고, 서비스가 data 의 매직 바이트와 맞춰 본다.
 * 클라이언트가 보낸 파일명은 일부러 담지 않는다 (저장 이름은 서버가 정한다).
 */
public record PhotoUpload(String contentType, byte[] data) {

    public boolean isEmpty() {
        return data == null || data.length == 0;
    }
}
