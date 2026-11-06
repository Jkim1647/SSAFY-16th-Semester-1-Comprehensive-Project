package com.ssafy.enjoytrip.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/** 따옴표("...")와 쉼표가 들어간 필드를 처리하는 최소 CSV 리더. 첫 줄은 헤더. */
public final class Csv {

    private Csv() {
    }

    public static List<Map<String, String>> readResource(String path) {
        List<Map<String, String>> rows = new ArrayList<>();
        forEachResource(path, rows::add);
        return rows;
    }

    /** 한 행씩 넘겨 주는 읽기. 행이 수만 개인 파일을 통째로 메모리에 올리지 않으려고 쓴다. 경로가 .gz 로 끝나면 gzip 으로 푼다. */
    public static void forEachResource(String path, Consumer<Map<String, String>> consumer) {
        InputStream in = Csv.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("리소스를 찾을 수 없습니다: " + path);
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                path.endsWith(".gz") ? new GZIPInputStream(in) : in, StandardCharsets.UTF_8))) {
            List<String> header = null;
            StringBuilder pending = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                pending.append(line);
                // 따옴표 안에 줄바꿈이 있으면 다음 줄까지 이어 붙인다
                if (countQuotes(pending) % 2 == 1) {
                    pending.append('\n');
                    continue;
                }
                List<String> fields = split(pending.toString());
                pending.setLength(0);
                if (header == null) {
                    fields.set(0, fields.get(0).replace("﻿", ""));
                    header = fields;
                    continue;
                }
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    row.put(header.get(i), i < fields.size() ? fields.get(i) : "");
                }
                consumer.accept(row);
            }
        } catch (IOException e) {
            throw new IllegalStateException("CSV 를 읽지 못했습니다: " + path, e);
        }
    }

    private static int countQuotes(CharSequence s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"') {
                n++;
            }
        }
        return n;
    }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
