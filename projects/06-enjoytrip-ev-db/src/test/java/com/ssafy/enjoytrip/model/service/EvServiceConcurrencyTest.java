package com.ssafy.enjoytrip.model.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.enjoytrip.model.dto.EvStation;
import com.ssafy.enjoytrip.model.service.EvService.Mode;
import com.ssafy.enjoytrip.model.service.EvService.Outcome;
import com.ssafy.enjoytrip.util.DBUtil;

/**
 * 같은 슬롯에 동시에 몰리는 예약을 실제 MySQL 에서 재현한다.
 * MySQL(enjoytrip DB, 앱을 한 번 실행해 테이블·충전소가 적재된 상태)이 없으면 건너뛴다.
 * 테스트 데이터는 실제 예약(run_id 0)·시뮬레이션과 겹치지 않는 run_id 로 넣고 끝나면 지운다.
 */
class EvServiceConcurrencyTest {

    private static final long TEST_RUN = 990_001;
    private static final int THREADS = 32;
    private final EvService service = EvService.getInstance();
    private EvStation station;
    private final LocalDateTime slot = LocalDate.now().plusDays(1).atTime(19, 0);

    @BeforeEach
    void setUp() {
        try (Connection con = DBUtil.getConnection()) {
            assumeTrue(con.isValid(2));
        } catch (SQLException e) {
            assumeTrue(false, "MySQL 에 연결할 수 없어 건너뜀: " + e.getMessage());
        }
        List<EvStation> list = service.nearby(35.1468, 126.9194, 10, 50, 50);
        station = list.stream().filter(s -> s.getChargerCount() >= 2).findFirst().orElse(null);
        assumeTrue(station != null, "충전기 2대 이상인 충전소 데이터가 없어 건너뜀");
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        if (station == null) {
            return;
        }
        try (Connection con = DBUtil.getConnection();
                PreparedStatement p1 = con.prepareStatement("DELETE FROM ev_reservations WHERE run_id = ?");
                PreparedStatement p2 = con.prepareStatement("DELETE FROM ev_slots WHERE run_id = ?")) {
            p1.setLong(1, TEST_RUN);
            p1.executeUpdate();
            p2.setLong(1, TEST_RUN);
            p2.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private int fire(Mode mode) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Outcome>> results = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            int vehicle = i + 1;
            results.add(pool.submit(() -> {
                go.await();
                return service.tryReserve(TEST_RUN, station, slot, null, vehicle, mode).outcome();
            }));
        }
        go.countDown(); // 32개 스레드가 같은 순간에 출발
        int confirmed = 0;
        for (Future<Outcome> f : results) {
            if (f.get() == Outcome.CONFIRMED) {
                confirmed++;
            }
        }
        pool.shutdown();
        return confirmed;
    }

    @Test
    void lockedModeNeverExceedsChargerCount() throws Exception {
        int confirmed = fire(Mode.LOCKED);
        assertEquals(station.getChargerCount(), confirmed, "잠금 예약은 정확히 충전기 수만큼만 확정되어야 한다");
        assertEquals(0, service.countOverbooked(TEST_RUN));
    }

    @Test
    void naiveModeOverbooksUnderContention() throws Exception {
        int confirmed = fire(Mode.NAIVE);
        // 잠금이 없으면 "세고 → 넣기" 사이에 끼어든 요청 때문에 정원을 넘긴다 (비교용으로 남겨 둔 동작)
        assertTrue(confirmed > station.getChargerCount(),
                "NAIVE 확정 " + confirmed + "건이 충전기 " + station.getChargerCount() + "대보다 많아야 한다");
        assertTrue(service.countOverbooked(TEST_RUN) > 0);
    }
}
