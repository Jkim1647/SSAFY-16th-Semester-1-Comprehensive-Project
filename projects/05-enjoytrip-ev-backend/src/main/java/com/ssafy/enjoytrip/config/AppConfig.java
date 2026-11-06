package com.ssafy.enjoytrip.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

/**
 * enjoytrip.properties 를 읽고, 같은 키의 시스템 속성(-Dkey)과 환경변수(ENJOYTRIP_KEY)로 덮어쓴다.
 * 우선순위: 시스템 속성 > 환경변수 > 파일. 비밀 값(DB 비밀번호, pepper)은 파일이 아니라 환경변수로 넣는다.
 */
public final class AppConfig {

    private static final Properties PROPS = load();

    private AppConfig() {
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("enjoytrip.properties")) {
            if (in == null) {
                throw new IllegalStateException("enjoytrip.properties 를 찾을 수 없습니다.");
            }
            p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("설정 파일을 읽지 못했습니다.", e);
        }
        return p;
    }

    public static String get(String key) {
        String sys = System.getProperty(key);
        if (sys != null) {
            return sys;
        }
        String env = System.getenv("ENJOYTRIP_" + key.toUpperCase().replace('.', '_'));
        if (env != null) {
            return env;
        }
        return PROPS.getProperty(key, "");
    }

    public static int getInt(String key, int defaultValue) {
        String v = get(key);
        try {
            return v.isBlank() ? defaultValue : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("설정 " + key + " 은 숫자여야 합니다: " + v);
        }
    }

    public static Path uploadDir() {
        String dir = get("upload.dir");
        return dir.isBlank() ? Path.of(System.getProperty("user.home"), "enjoytrip-uploads") : Path.of(dir);
    }
}
