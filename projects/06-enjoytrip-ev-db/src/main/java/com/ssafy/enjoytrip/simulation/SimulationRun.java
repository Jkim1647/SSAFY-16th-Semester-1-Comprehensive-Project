package com.ssafy.enjoytrip.simulation;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.ssafy.enjoytrip.model.dto.EvStation;
import com.ssafy.enjoytrip.model.service.EvService.Mode;

/**
 * 실행 중인 시뮬레이션 한 번의 상태. 작업자 스레드 여러 개가 동시에 갱신하므로 카운터는 Atomic, 배열 칸은 차량별로 한 스레드만 쓴다
 * (같은 차량의 요청은 한 번에 하나만 큐에 있다).
 */
public class SimulationRun {

    public static final byte IDLE = 0, QUEUED = 1, RETRYING = 2, CONFIRMED = 3, REJECTED = 4;
    private static final char[] STATE_CODE = { 'i', 'q', 'w', 'c', 'r' };
    private static final int LOG_SIZE = 300;

    final long runId;
    final Mode mode;
    final int vehicleCount;
    final List<EvStation> stations;
    final LocalDateTime slot;
    final long startedAt = System.currentTimeMillis();
    volatile long finishedAt;
    volatile String status = "RUNNING";
    volatile String queueLabel = "";
    volatile int overbooked = -1;
    final Map<String, String> notes = new LinkedHashMap<>();

    /** 차량별 출발 배터리(%), 희망 충전소 순서, 현재 상태, 배정된 좌석 */
    final int[] soc;
    final int[][] choices;
    final byte[] state;
    final int[] ticket;
    final int[] seatStation;
    final int[] seatCharger;
    final int myCar;

    final AtomicInteger published = new AtomicInteger();
    final AtomicInteger processed = new AtomicInteger();
    final AtomicInteger confirmed = new AtomicInteger();
    final AtomicInteger rejected = new AtomicInteger();
    final AtomicInteger retries = new AtomicInteger();
    final AtomicInteger errors = new AtomicInteger();

    private final Deque<String[]> logs = new ArrayDeque<>();
    private long logSeq;

    SimulationRun(long runId, Mode mode, int vehicleCount, List<EvStation> stations, LocalDateTime slot,
            int[] soc, int[][] choices, int myCar) {
        this.runId = runId;
        this.mode = mode;
        this.vehicleCount = vehicleCount;
        this.stations = stations;
        this.slot = slot;
        this.soc = soc;
        this.choices = choices;
        this.myCar = myCar;
        this.state = new byte[vehicleCount];
        this.ticket = new int[vehicleCount];
        this.seatStation = new int[vehicleCount];
        this.seatCharger = new int[vehicleCount];
        java.util.Arrays.fill(seatStation, -1);
    }

    public long getRunId() {
        return runId;
    }

    public boolean isRunning() {
        return "RUNNING".equals(status);
    }

    boolean isDone() {
        return confirmed.get() + rejected.get() >= vehicleCount;
    }

    /** kind: ok | full | retry | reject | info */
    synchronized void log(String kind, String text) {
        logs.addLast(new String[] { String.valueOf(++logSeq), kind, text });
        if (logs.size() > LOG_SIZE) {
            logs.removeFirst();
        }
    }

    synchronized List<Map<String, Object>> logsSince(long since) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] l : logs) {
            long seq = Long.parseLong(l[0]);
            if (seq > since) {
                out.add(Map.of("seq", seq, "kind", l[1], "text", l[2]));
            }
        }
        // 한 번에 너무 많이 보내지 않도록 최근 60줄만
        return out.size() > 60 ? out.subList(out.size() - 60, out.size()) : out;
    }

    String stateString() {
        char[] c = new char[vehicleCount];
        for (int i = 0; i < vehicleCount; i++) {
            c[i] = STATE_CODE[state[i]];
        }
        return new String(c);
    }

    long elapsedMs() {
        return (finishedAt > 0 ? finishedAt : System.currentTimeMillis()) - startedAt;
    }
}
