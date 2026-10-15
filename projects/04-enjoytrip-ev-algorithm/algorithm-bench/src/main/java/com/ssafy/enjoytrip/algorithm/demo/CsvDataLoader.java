package com.ssafy.enjoytrip.algorithm.demo;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import com.ssafy.enjoytrip.algorithm.geo.Location;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * data/*.csv 를 읽는 데모용 로더. 필드에 쉼표가 없도록 저장해 두었으므로 단순 split 으로 충분하다.
 * BackEnd 에서는 이 역할을 MyBatis/JPA DAO 가 맡는다 (attraction, ev_station 테이블).
 */
public final class CsvDataLoader {

    private CsvDataLoader() {
    }

    /** contentId,title,lat,lng,addr,lDongSignguCd */
    public static List<Location> attractions(Path csv) throws IOException {
        List<Location> list = new ArrayList<Location>();
        for (String[] f : rows(csv)) list.add(new Location(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3])));
        return list;
    }

    /** contentId,title,lat,lng,addr,lDongSignguCd,lcls1,lcls2,lcls3 */
    public static List<Attraction> attractionDetails(Path csv) throws IOException {
        List<Attraction> list = new ArrayList<Attraction>();
        for (String[] f : rows(csv)) {
            list.add(new Attraction(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]), f[4],
                    f.length > 6 ? f[6] : "", f.length > 7 ? f[7] : "", f.length > 8 ? f[8] : ""));
        }
        return list;
    }

    /** code,name (한국관광공사 신분류체계 코드 → 이름) */
    public static Map<String, String> codeNames(Path csv) throws IOException {
        Map<String, String> map = new HashMap<String, String>();
        for (String[] f : rows(csv)) map.put(f[0], f[1]);
        return map;
    }

    /** statId,statNm,lat,lng,maxKw,chargers */
    public static List<ChargingStation> stations(Path csv) throws IOException {
        List<ChargingStation> list = new ArrayList<ChargingStation>();
        for (String[] f : rows(csv)) {
            list.add(new ChargingStation(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]), Double.parseDouble(f[4])));
        }
        return list;
    }

    private static List<String[]> rows(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        List<String[]> rows = new ArrayList<String[]>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) rows.add(line.split(",", -1));
        }
        return rows;
    }
}
