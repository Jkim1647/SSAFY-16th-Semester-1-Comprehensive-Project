package com.ssafy.enjoytrip.model.dao;

import com.ssafy.enjoytrip.config.EnjoyTripProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * data/*.csv 를 읽는다. 필드에 쉼표가 없도록 저장해 두었으므로 단순 split 으로 충분하다.
 * 데이터 폴더는 설정값 그대로 찾고, 없으면 상위 폴더에서 찾는다 (IDE 에서 backend 모듈을 실행하면 작업 폴더가 backend/ 이므로).
 */
@Component
public class CsvDataSource {

    private static final Logger log = LoggerFactory.getLogger(CsvDataSource.class);

    private final Path dataDir;

    public CsvDataSource(EnjoyTripProperties properties) {
        Path configured = Paths.get(properties.getDataDir());
        Path parent = Paths.get("..").resolve(properties.getDataDir());
        if (Files.isDirectory(configured)) dataDir = configured.toAbsolutePath().normalize();
        else if (!configured.isAbsolute() && Files.isDirectory(parent)) dataDir = parent.toAbsolutePath().normalize();
        else throw new IllegalStateException("데이터 폴더를 찾을 수 없습니다: " + configured.toAbsolutePath()
                    + " (enjoytrip.data-dir 설정이나 실행 위치를 확인하세요)");
        log.info("CSV 데이터 폴더: {}", dataDir);
    }

    /** 첫 줄(헤더)을 뺀 행들을 쉼표로 나눠 돌려준다 */
    List<String[]> rows(String fileName) {
        Path file = dataDir.resolve(fileName);
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String[]> rows = new ArrayList<>(Math.max(0, lines.size() - 1));
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) rows.add(line.split(",", -1));
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("CSV 를 읽을 수 없습니다: " + file, e);
        }
    }
}
