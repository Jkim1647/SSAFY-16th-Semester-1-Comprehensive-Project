package com.ssafy.enjoytrip.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.ssafy.enjoytrip.config.AppConfig;

/**
 * 업로드 사진 파일 저장소 (AppConfig.uploadDir()).
 * - 파일 종류는 확장자나 선언된 Content-Type 이 아니라 파일 앞부분의 매직 바이트로 판별한다.
 * - 저장 이름은 "UUID.판별한 확장자" 로 서버가 정한다. 사용자가 보낸 파일명은 경로에 절대 쓰지 않는다.
 * - 읽기·삭제도 이 이름 형식에 맞는 것만 허용해 ../ 같은 경로 조작을 막는다.
 */
public final class PhotoStorage {

    /** 저장·서빙을 허용하는 파일명: 소문자 UUID(36자) + 허용 확장자 */
    private static final Pattern NAME = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp|gif)");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg", "png", "image/png", "webp", "image/webp", "gif", "image/gif");

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] GIF87 = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF89 = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private PhotoStorage() {
    }

    /** 매직 바이트로 이미지 종류를 판별해 확장자(jpg/png/webp/gif)를 돌려준다. 허용 형식이 아니면 null. */
    public static String detectExtension(byte[] data) {
        if (data == null) {
            return null;
        }
        if (startsWith(data, 0, JPEG)) {
            return "jpg";
        }
        if (startsWith(data, 0, PNG)) {
            return "png";
        }
        if (startsWith(data, 0, GIF87) || startsWith(data, 0, GIF89)) {
            return "gif";
        }
        if (startsWith(data, 0, RIFF) && startsWith(data, 8, WEBP)) {
            return "webp";
        }
        return null;
    }

    /** 브라우저가 선언한 Content-Type 을 확장자로 바꾼다. 허용 목록 밖이면 null. */
    public static String extensionForContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String type = contentType.split(";", 2)[0].trim().toLowerCase();
        return switch (type) {
            case "image/jpeg", "image/jpg", "image/pjpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> null;
        };
    }

    /** 서빙할 때 쓸 Content-Type. 파일명이 형식에 맞지 않으면 null. */
    public static String contentType(String fileName) {
        if (!isValidName(fileName)) {
            return null;
        }
        return CONTENT_TYPES.get(fileName.substring(fileName.lastIndexOf('.') + 1));
    }

    public static boolean isValidName(String fileName) {
        return fileName != null && NAME.matcher(fileName).matches();
    }

    /** 파일명이 형식에 맞으면 업로드 폴더 안의 경로를, 아니면 null 을 돌려준다. */
    public static Path resolve(String fileName) {
        if (!isValidName(fileName)) {
            return null;
        }
        Path dir = AppConfig.uploadDir().toAbsolutePath().normalize();
        Path file = dir.resolve(fileName).normalize();
        // 이름 형식으로 이미 막히지만, 폴더 밖을 가리키는 경로는 한 번 더 거른다
        return file.getParent().equals(dir) ? file : null;
    }

    /** 이미 판별이 끝난 이미지 바이트를 UUID 이름으로 저장하고 파일명을 돌려준다. 폴더가 없으면 만든다. */
    public static String save(byte[] data, String extension) {
        if (!CONTENT_TYPES.containsKey(extension)) {
            throw new IllegalArgumentException("허용하지 않는 확장자: " + extension);
        }
        String fileName = UUID.randomUUID() + "." + extension;
        try {
            Path dir = AppConfig.uploadDir().toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return fileName;
        } catch (IOException e) {
            throw new UncheckedIOException("사진 저장 실패", e);
        }
    }

    /** 사진 파일을 지운다. DB 작업은 이미 끝난 뒤라 실패해도 요청을 실패시키지 않고 로그만 남긴다. */
    public static void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        Path file = resolve(fileName);
        if (file == null) {
            System.err.println("사진 삭제 건너뜀 (잘못된 파일명): " + fileName);
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("사진 삭제 실패: " + fileName + " - " + e.getMessage());
        }
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
