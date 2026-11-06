package com.ssafy.enjoytrip.simulation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ssafy.enjoytrip.config.AppConfig;
import com.ssafy.enjoytrip.model.dao.JdbcSimRunDao;
import com.ssafy.enjoytrip.model.dao.SimRunDao;
import com.ssafy.enjoytrip.model.dto.EvStation;
import com.ssafy.enjoytrip.model.dto.Reservation;
import com.ssafy.enjoytrip.model.dto.SimRunSummary;
import com.ssafy.enjoytrip.model.service.EvService;
import com.ssafy.enjoytrip.model.service.EvService.Mode;
import com.ssafy.enjoytrip.model.service.EvService.Outcome;
import com.ssafy.enjoytrip.model.service.EvService.Result;
import com.ssafy.enjoytrip.model.service.ValidationException;

/**
 * "연휴 전날 저녁 7시, 광주 도심 급속충전소에 전기차 N대가 동시에 예약" 을 재현한다.
 *
 * <ol>
 *   <li>도심에서 가까운 급속충전소 K곳을 고르고, 가상 차량마다 배터리 잔량과 희망 충전소 3곳(1~3지망)을 정한다.</li>
 *   <li>차량들이 burstMs 동안 몰려 큐에 줄을 선다 (줄 선 순서 = 대기 번호).</li>
 *   <li>작업자가 큐에서 꺼내 EvService.tryReserve 로 실제 DB 예약을 시도한다 — 회원 예약과 같은 코드 경로.</li>
 *   <li>자리가 없으면 다음 지망으로 다시 줄을 서고, 3지망까지 실패하면 예약 실패.</li>
 *   <li>모두 끝나면 DB 에서 슬롯별 정원 초과(오버부킹)를 집계해 기록한다.</li>
 * </ol>
 * 한 번에 하나의 실행만 돌린다 (DB 연결과 화면을 독점하므로).
 */
public final class SimulationEngine {

    /** 광주 도심 기준점 (5·18 민주광장 부근). */
    private static final double CENTER_LAT = 35.1468, CENTER_LNG = 126.9194;
    private static final int WORKERS = 16;
    private static final int MAX_CHOICES = 3;
    private static final long TIMEOUT_MS = 180_000;
    private static final SimulationEngine INSTANCE = new SimulationEngine();

    private final EvService evService = EvService.getInstance();
    private final SimRunDao runDao = JdbcSimRunDao.getInstance();
    private final Object lock = new Object();
    private SimulationRun current;
    private ReservationQueue queue;
    private Map<String, Integer> stationIndex = Map.of();
    private final AtomicBoolean finishing = new AtomicBoolean();

    private SimulationEngine() {
    }

    public static SimulationEngine getInstance() {
        return INSTANCE;
    }

    /** 시뮬레이션 조건. queueType: memory | kafka */
    public record Config(int vehicles, int stations, Mode mode, String queueType, boolean partitioned, int burstMs, long seed) {
        public Config {
            if (vehicles < 10 || vehicles > 3000) {
                throw new ValidationException("차량 수는 10~3000 대로 정하세요.");
            }
            if (stations < 1 || stations > 60) {
                throw new ValidationException("충전소 수는 1~60 곳으로 정하세요.");
            }
            burstMs = Math.max(0, Math.min(burstMs, 10_000));
        }
    }

    public long start(Config cfg) {
        synchronized (lock) {
            if (current != null && "RUNNING".equals(current.status)) {
                throw new ValidationException("이미 시뮬레이션이 진행 중입니다. 끝난 뒤 다시 시작하세요.");
            }
            List<EvStation> stations = evService.nearby(CENTER_LAT, CENTER_LNG, 12, 50, cfg.stations());
            if (stations.isEmpty()) {
                throw new ValidationException("도심 근처 급속충전소 데이터가 없습니다.");
            }
            // 연휴 전날 저녁 7시라는 설정이지만, 실제 회원 예약과 섞이지 않게 run_id 로 분리되므로 날짜는 내일로 고정한다
            LocalDateTime slot = LocalDate.now().plusDays(1).atTime(LocalTime.of(19, 0));
            Random rnd = cfg.seed() == 0 ? new Random() : new Random(cfg.seed());

            int n = cfg.vehicles();
            int[] soc = new int[n];
            int[][] choices = new int[n][];
            for (int v = 0; v < n; v++) {
                soc[v] = 5 + (int) Math.round(Math.abs(rnd.nextGaussian()) * 18) % 55; // 대부분 5~35%, 가끔 더 높음
                choices[v] = pickChoices(rnd, stations.size());
            }
            int myCar = rnd.nextInt(n);
            soc[myCar] = 12; // 인트로의 "배터리 12%" 운전자

            boolean wantKafka = "kafka".equalsIgnoreCase(cfg.queueType());
            evService.purgeOldRuns(5);
            long runId = runDao.insert(cfg.mode().name(), wantKafka ? "kafka" : "memory", n, stations.size());
            SimulationRun run = new SimulationRun(runId, cfg.mode(), n, stations, slot, soc, choices, myCar);

            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < stations.size(); i++) {
                index.put(stations.get(i).getStatId(), i);
            }
            ReservationQueue q = wantKafka
                    ? KafkaReservationQueue.createOrFallback(AppConfig.get("kafka.bootstrap"), AppConfig.get("kafka.topic"), runId,
                            WORKERS, run.notes)
                    : new MemoryReservationQueue(WORKERS, cfg.partitioned());
            run.queueLabel = q.label();
            run.notes.forEach((k, msg) -> run.log("info", msg));

            this.current = run;
            this.queue = q;
            this.stationIndex = index;
            this.finishing.set(false);
            q.start(req -> handle(run, req));
            startProducer(run, q, cfg.burstMs(), rnd);
            startWatchdog(run);
            run.log("info", String.format("시뮬레이션 #%d 시작 — 차량 %,d대 · 충전소 %d곳(좌석 %d) · %s · %s",
                    runId, n, stations.size(), stations.stream().mapToInt(EvStation::getChargerCount).sum(),
                    cfg.mode() == Mode.LOCKED ? "잠금 예약" : "잠금 없음", q.label()));
            return runId;
        }
    }

    /** 1지망은 도심에 가까운 충전소일수록 몰리게(지프 분포), 2·3지망은 1지망과 다른 곳 중에서. */
    private static int[] pickChoices(Random rnd, int k) {
        int count = Math.min(MAX_CHOICES, k);
        List<Integer> picked = new ArrayList<>();
        double total = 0;
        for (int i = 1; i <= k; i++) {
            total += 1.0 / Math.pow(i, 0.9);
        }
        double r = rnd.nextDouble() * total;
        int first = 0;
        for (int i = 1; i <= k; i++) {
            r -= 1.0 / Math.pow(i, 0.9);
            if (r <= 0) {
                first = i - 1;
                break;
            }
        }
        picked.add(first);
        while (picked.size() < count) {
            int c = Math.min(k - 1, Math.max(0, first + (int) Math.round(rnd.nextGaussian() * 4)));
            if (!picked.contains(c)) {
                picked.add(c);
            } else if (rnd.nextDouble() < 0.3) {
                int any = rnd.nextInt(k);
                if (!picked.contains(any)) {
                    picked.add(any);
                }
            }
        }
        return picked.stream().mapToInt(Integer::intValue).toArray();
    }

    private void startProducer(SimulationRun run, ReservationQueue q, int burstMs, Random rnd) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < run.vehicleCount; i++) {
            order.add(i);
        }
        Collections.shuffle(order, rnd);
        Thread t = new Thread(() -> {
            long start = System.currentTimeMillis();
            for (int i = 0; i < order.size(); i++) {
                int v = order.get(i);
                run.ticket[v] = i + 1;
                run.state[v] = SimulationRun.QUEUED;
                q.publish(new ReservationRequest(run.runId, v, i + 1, 0, run.stations.get(run.choices[v][0]).getStatId()));
                run.published.incrementAndGet();
                long due = start + (long) burstMs * (i + 1) / order.size();
                long wait = due - System.currentTimeMillis();
                if (wait > 0) {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            run.log("info", String.format("차량 %,d대가 모두 줄을 섰습니다 (%,d ms).", order.size(), System.currentTimeMillis() - start));
        }, "sim-producer-" + run.runId);
        t.setDaemon(true);
        t.start();
    }

    private void startWatchdog(SimulationRun run) {
        Thread t = new Thread(() -> {
            while ("RUNNING".equals(run.status)) {
                if (System.currentTimeMillis() - run.startedAt > TIMEOUT_MS) {
                    run.log("reject", "제한 시간을 넘겨 시뮬레이션을 중단합니다.");
                    finish(run, "FAILED");
                    return;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "sim-watchdog-" + run.runId);
        t.setDaemon(true);
        t.start();
    }

    /** 작업자 스레드에서 호출: 예약 한 번 시도하고 결과에 따라 확정·재시도·실패 처리. */
    private void handle(SimulationRun run, ReservationRequest req) {
        if (!"RUNNING".equals(run.status)) {
            return;
        }
        int v = req.vehicleNo();
        Integer idx = stationIndex.get(req.statId());
        EvStation station = run.stations.get(idx);
        String who = String.format("대기 %,d번(차량 %d, %d%%)", req.ticketNo(), v + 1, run.soc[v]);
        try {
            Result result = evService.tryReserve(run.runId, station, run.slot, null, v + 1, run.mode);
            run.processed.incrementAndGet();
            if (result.outcome() == Outcome.CONFIRMED) {
                run.seatStation[v] = idx;
                run.seatCharger[v] = result.chargerNo();
                run.state[v] = SimulationRun.CONFIRMED;
                run.confirmed.incrementAndGet();
                run.log("ok", who + " → " + station.getName() + " " + result.chargerNo() + "번 충전기 확정");
            } else {
                retryOrReject(run, req, who, station);
            }
        } catch (RuntimeException e) {
            run.processed.incrementAndGet();
            run.errors.incrementAndGet();
            run.log("reject", who + " → 처리 오류: " + e.getMessage());
            run.state[v] = SimulationRun.REJECTED;
            run.rejected.incrementAndGet();
        }
        if (run.isDone()) {
            finish(run, "DONE");
        }
    }

    private void retryOrReject(SimulationRun run, ReservationRequest req, String who, EvStation full) {
        int v = req.vehicleNo();
        int[] choices = run.choices[v];
        if (req.attempt() + 1 < choices.length) {
            EvStation next = run.stations.get(choices[req.attempt() + 1]);
            run.state[v] = SimulationRun.RETRYING;
            run.retries.incrementAndGet();
            run.log("retry", who + " → " + full.getName() + " 만석, " + (req.attempt() + 2) + "지망 " + next.getName() + "(으)로");
            queue.publish(req.next(next.getStatId()));
        } else {
            run.state[v] = SimulationRun.REJECTED;
            run.rejected.incrementAndGet();
            run.log("reject", who + " → 3지망까지 만석, 예약 실패" + (run.soc[v] < 15 ? " ⚠ 방전 위험" : ""));
        }
    }

    private void finish(SimulationRun run, String status) {
        if (!finishing.compareAndSet(false, true)) {
            return;
        }
        run.finishedAt = System.currentTimeMillis();
        try {
            run.overbooked = evService.countOverbooked(run.runId);
        } catch (RuntimeException e) {
            run.overbooked = -1;
        }
        run.status = status;
        runDao.finish(run.runId, status, run.queueLabel, run.confirmed.get(), run.rejected.get(), Math.max(0, run.overbooked));
        run.log(run.overbooked > 0 ? "reject" : "info", String.format("종료 — 확정 %,d · 실패 %,d · 오버부킹 %d건 (%,d ms)",
                run.confirmed.get(), run.rejected.get(), run.overbooked, run.elapsedMs()));
        ReservationQueue q = this.queue;
        if (q != null) {
            // 작업자 스레드 자신이 finish 를 부를 수 있으므로 별도 스레드에서 닫는다
            Thread closer = new Thread(q::close, "sim-close-" + run.runId);
            closer.setDaemon(true);
            closer.start();
        }
    }

    /** 화면 폴링용 상태. since 이후 로그만, full=true 면 차량 배터리 같은 고정 정보도 함께. */
    public Map<String, Object> status(long runId, long since, boolean full) {
        SimulationRun run = current;
        if (run == null || run.runId != runId) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", run.runId);
        m.put("status", run.status);
        m.put("mode", run.mode.name());
        m.put("queue", run.queueLabel);
        m.put("vehicles", run.vehicleCount);
        m.put("elapsedMs", run.elapsedMs());
        m.put("counts", Map.of("published", run.published.get(), "processed", run.processed.get(),
                "confirmed", run.confirmed.get(), "rejected", run.rejected.get(), "retries", run.retries.get(),
                "queued", queue == null ? 0 : queue.depth(), "errors", run.errors.get()));
        m.put("states", run.stateString());
        m.put("overbooked", "RUNNING".equals(run.status) ? null : run.overbooked);
        m.put("logs", run.logsSince(since));
        int v = run.myCar;
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("vehicleNo", v + 1);
        me.put("ticketNo", run.ticket[v]);
        me.put("soc", run.soc[v]);
        me.put("state", String.valueOf(run.stateString().charAt(v)));
        me.put("station", run.seatStation[v] >= 0 ? run.stations.get(run.seatStation[v]).getName() : null);
        me.put("chargerNo", run.seatCharger[v]);
        m.put("myCar", me);
        m.put("seats", seatBoard(run));
        if (full) {
            m.put("soc", run.soc);
            m.put("slot", run.slot.toString());
            m.put("lowBatteryRejected", lowBatteryRejected(run));
        }
        return m;
    }

    private static int lowBatteryRejected(SimulationRun run) {
        int c = 0;
        for (int i = 0; i < run.vehicleCount; i++) {
            if (run.state[i] == SimulationRun.REJECTED && run.soc[i] < 15) {
                c++;
            }
        }
        return c;
    }

    /** 좌석 배치도: 충전소(행) × 충전기(좌석). DB 에 실제로 들어간 예약으로 그려서, 정원을 넘긴 예약이 그대로 드러난다. */
    private List<Map<String, Object>> seatBoard(SimulationRun run) {
        Map<String, List<Integer>> byStation = new HashMap<>();
        for (Reservation r : runDao.findSeats(run.runId)) {
            byStation.computeIfAbsent(r.getStatId(), k -> new ArrayList<>()).add(r.getVehicleNo());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EvStation s : run.stations) {
            List<Integer> vehicles = byStation.getOrDefault(s.getStatId(), List.of());
            rows.add(Map.of("name", s.getName(), "capacity", s.getChargerCount(), "kw", s.getMaxKw(),
                    "booked", vehicles));
        }
        return rows;
    }

    public SimulationRun current() {
        return current;
    }

    public List<SimRunSummary> history(int limit) {
        return runDao.recent(limit);
    }

    public void shutdown() {
        ReservationQueue q = this.queue;
        if (q != null) {
            q.close();
        }
        SimulationRun run = current;
        if (run != null && "RUNNING".equals(run.status)) {
            run.status = "FAILED";
        }
    }
}
