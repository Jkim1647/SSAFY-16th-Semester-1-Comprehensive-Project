package com.ssafy.enjoytrip.algorithm.demo;

import com.ssafy.enjoytrip.algorithm.ev.ChargingStation;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlan;
import com.ssafy.enjoytrip.algorithm.ev.EvRoutePlanner;
import com.ssafy.enjoytrip.algorithm.ev.VehicleSpec;
import com.ssafy.enjoytrip.algorithm.geo.Location;
import com.ssafy.enjoytrip.algorithm.trip.EvTripPlan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 시연 결과를 web/index.html 이 읽는 JS 파일(window.DEMO_RESULT = {...})로 쓴다.
 * file:// 로 열어도 동작하도록 fetch 대신 script 태그로 읽는 형식을 쓴다.
 * BackEnd 에서는 같은 구조를 Controller 가 JSON 으로 응답하면 된다.
 */
final class WebResultWriter {

    private WebResultWriter() {
    }

    static void write(Path out, Location start, List<Location> asIsOrder, double asIsKm, EvTripPlan trip,
                      VehicleSpec vehicle, double startSoc, List<ChargingStation> stations, long elapsedMs) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("// AlgorithmDemo 가 생성한 파일 (sh scripts/run.sh web). 직접 수정하지 마세요.\n");
        sb.append("window.DEMO_RESULT = {\n");
        sb.append("  \"vehicle\": {\"batteryKwh\": ").append(num(vehicle.getBatteryKwh()))
                .append(", \"rangeKm\": ").append(num(vehicle.fullRangeKm()))
                .append(", \"startSoc\": ").append(num(startSoc))
                .append(", \"reserveSoc\": ").append(num(vehicle.getReserveSoc()))
                .append(", \"targetSoc\": ").append(num(vehicle.getTargetSoc())).append("},\n");
        sb.append("  \"start\": ").append(point(start)).append(",\n");
        sb.append("  \"asIs\": {\"km\": ").append(num(asIsKm)).append(", \"stops\": ").append(points(asIsOrder)).append("},\n");
        sb.append("  \"optimized\": {\"km\": ").append(num(trip.getRoute().getTotalKm()))
                .append(", \"method\": ").append(str(trip.getRoute().getMethod().name()))
                .append(", \"stops\": ").append(points(trip.getRoute().getOrderedStops())).append("},\n");
        sb.append("  \"legs\": [\n");
        List<EvRoutePlan> legs = trip.getLegs();
        for (int i = 0; i < legs.size(); i++) {
            EvRoutePlan leg = legs.get(i);
            sb.append("    {\"from\": ").append(str(leg.getOrigin().getName()))
                    .append(", \"to\": ").append(str(leg.getDestination().getName()))
                    .append(", \"feasible\": ").append(leg.isFeasible())
                    .append(", \"km\": ").append(num(leg.getTotalKm()))
                    .append(", \"driveMin\": ").append(num(leg.getDriveMin()))
                    .append(", \"chargeMin\": ").append(num(leg.getChargeMin()))
                    .append(", \"arrivalSoc\": ").append(num(leg.getArrivalSoc()))
                    .append(", \"candidates\": ").append(leg.getCandidateCount())
                    .append(", \"charges\": [");
            List<EvRoutePlan.ChargeStop> stops = leg.getStops();
            for (int j = 0; j < stops.size(); j++) {
                EvRoutePlan.ChargeStop s = stops.get(j);
                if (j > 0) sb.append(", ");
                sb.append("{\"name\": ").append(str(s.getStation().getName()))
                        .append(", \"lat\": ").append(num(s.getStation().getLat()))
                        .append(", \"lng\": ").append(num(s.getStation().getLng()))
                        .append(", \"kw\": ").append(num(s.getStation().getMaxKw()))
                        .append(", \"arriveSoc\": ").append(num(s.getArriveSoc()))
                        .append(", \"departSoc\": ").append(num(s.getDepartSoc()))
                        .append(", \"min\": ").append(num(s.getChargeMin())).append("}");
            }
            sb.append("]}").append(i < legs.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n");
        sb.append("  \"summary\": {\"km\": ").append(num(trip.getTotalKm()))
                .append(", \"totalMin\": ").append(num(trip.getTotalMin()))
                .append(", \"charges\": ").append(trip.getChargeStopCount())
                .append(", \"feasible\": ").append(trip.isFeasible())
                .append(", \"elapsedMs\": ").append(elapsedMs)
                .append(", \"stationCount\": ").append(stations.size()).append("},\n");
        // 지도 배경용 급속 충전소 좌표 [lat, lng, kW]
        sb.append("  \"fastStations\": [");
        boolean first = true;
        for (ChargingStation s : stations) {
            if (s.getMaxKw() < EvRoutePlanner.DEFAULT_MIN_CHARGER_KW) continue;
            sb.append(first ? "" : ",").append("[").append(num(s.getLat())).append(",").append(num(s.getLng()))
                    .append(",").append(num(s.getMaxKw())).append("]");
            first = false;
        }
        sb.append("]\n};\n");
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.write(out, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String points(List<Location> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) sb.append(i > 0 ? ", " : "").append(point(list.get(i)));
        return sb.append("]").toString();
    }

    private static String point(Location l) {
        return "{\"id\": " + str(l.getId()) + ", \"name\": " + str(l.getName())
                + ", \"lat\": " + num(l.getLat()) + ", \"lng\": " + num(l.getLng()) + "}";
    }

    private static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "null";
        return String.format(Locale.ROOT, "%.6f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c == '<') sb.append("\\u003c"); // </script> 방지
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }
}
