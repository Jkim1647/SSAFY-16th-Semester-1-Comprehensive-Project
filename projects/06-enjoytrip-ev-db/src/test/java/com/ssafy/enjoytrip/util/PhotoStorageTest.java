package com.ssafy.enjoytrip.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PhotoStorageTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    @Test
    void 매직_바이트로_이미지_종류를_판별한다() {
        assertEquals("png", PhotoStorage.detectExtension(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0)));
        assertEquals("jpg", PhotoStorage.detectExtension(bytes(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals("gif", PhotoStorage.detectExtension("GIF89a...".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("webp", PhotoStorage.detectExtension("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void 이미지가_아니면_null() {
        assertNull(PhotoStorage.detectExtension("hello, I am text".getBytes(StandardCharsets.US_ASCII)));
        assertNull(PhotoStorage.detectExtension("<svg xmlns=".getBytes(StandardCharsets.US_ASCII)));
        assertNull(PhotoStorage.detectExtension("RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.US_ASCII)));
        assertNull(PhotoStorage.detectExtension(new byte[0]));
        assertNull(PhotoStorage.detectExtension(null));
    }

    @Test
    void 선언된_Content_Type_은_허용_목록만_통과한다() {
        assertEquals("jpg", PhotoStorage.extensionForContentType("image/jpeg"));
        assertEquals("png", PhotoStorage.extensionForContentType("IMAGE/PNG; charset=binary"));
        assertNull(PhotoStorage.extensionForContentType("image/svg+xml"));
        assertNull(PhotoStorage.extensionForContentType("text/html"));
        assertNull(PhotoStorage.extensionForContentType(null));
    }

    @Test
    void 서버가_만든_이름_형식만_허용한다() {
        assertTrue(PhotoStorage.isValidName("3f2b8c1e-0a4d-4f6b-9c2e-1d2e3f4a5b6c.png"));
        assertFalse(PhotoStorage.isValidName("../3f2b8c1e-0a4d-4f6b-9c2e-1d2e3f4a5b6.png"));
        assertFalse(PhotoStorage.isValidName("3f2b8c1e-0a4d-4f6b-9c2e-1d2e3f4a5b6c.svg"));
        assertFalse(PhotoStorage.isValidName("3F2B8C1E-0A4D-4F6B-9C2E-1D2E3F4A5B6C.png"));
        assertFalse(PhotoStorage.isValidName("web.xml"));
        assertNull(PhotoStorage.resolve("..\\..\\secret.png"));
        assertEquals("image/webp", PhotoStorage.contentType("3f2b8c1e-0a4d-4f6b-9c2e-1d2e3f4a5b6c.webp"));
    }
}
