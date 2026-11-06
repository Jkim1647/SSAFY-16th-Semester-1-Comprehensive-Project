package com.ssafy.enjoytrip.model.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;

import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.model.service.EvService.Outcome;

/** 실제 회원 예약 경로 검증. 테스트 전용 회원·충전소만 생성하고 제거한다. */
class EvReservationIntegrityTest {
    private final EvService service = EvService.getInstance();
    private final List<Long> members = new ArrayList<>();
    private final String stationA = "test-" + UUID.randomUUID().toString().substring(0, 10);
    private final String stationB = stationA + "-b";
    private final LocalDateTime slot = LocalDate.now().plusDays(1).atTime(18, 0);
    private boolean initialized;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection con = DBUtil.getConnection()) {
            assumeTrue(con.isValid(2));
        } catch (SQLException e) {
            assumeTrue(false, "MySQL unavailable: " + e.getMessage());
        }
        initialized = true;
        try (Connection con = DBUtil.getConnection()) {
            for (String id : List.of(stationA, stationB)) {
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO ev_stations"
                        + " (stat_id,name,latitude,longitude,max_kw,charger_count) VALUES (?, 'test',35,126,50,3)")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
            }
            for (int i = 0; i < 4; i++) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO members(email,name,password_hash) VALUES (?, 'test', 'unused')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, UUID.randomUUID() + "@example.invalid");
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        members.add(rs.getLong(1));
                    }
                }
            }
        }
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (!initialized) return;
        try (Connection con = DBUtil.getConnection()) {
            for (String table : List.of("ev_reservations", "ev_slots", "ev_stations")) {
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM " + table + " WHERE stat_id IN (?, ?)")) {
                    ps.setString(1, stationA);
                    ps.setString(2, stationB);
                    ps.executeUpdate();
                }
            }
            for (long id : members) {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM members WHERE member_id=?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Test
    void cancellationReusesVacantChargerAndRestoresCapacity() {
        var first = service.reserve(members.get(0), stationA, slot);
        service.reserve(members.get(1), stationA, slot);
        service.reserve(members.get(2), stationA, slot);
        assertEquals(Outcome.FULL, service.reserve(members.get(3), stationA, slot).outcome());
        service.cancel(members.get(0), first.reservationId());
        var replacement = service.reserve(members.get(3), stationA, slot);
        assertEquals(Outcome.CONFIRMED, replacement.outcome());
        assertEquals(first.chargerNo(), replacement.chargerNo());
        var confirmed = service.dayBoard(0, service.findStation(stationA), slot.toLocalDate())
                .get(slot.getHour()).getReservations();
        assertEquals(3, confirmed.size());
        assertEquals(3, confirmed.stream().map(r -> r.getChargerNo()).distinct().count());
    }

    @Test
    void sameMemberCannotReserveDifferentStationsConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Outcome>> results = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                String station = i % 2 == 0 ? stationA : stationB;
                results.add(pool.submit(() -> {
                    go.await();
                    return service.reserve(members.get(0), station, slot).outcome();
                }));
            }
            go.countDown();
            int confirmed = 0, duplicates = 0;
            for (Future<Outcome> result : results) {
                Outcome outcome = result.get(30, TimeUnit.SECONDS);
                if (outcome == Outcome.CONFIRMED) confirmed++;
                if (outcome == Outcome.DUPLICATE) duplicates++;
            }
            assertEquals(1, confirmed);
            assertEquals(15, duplicates);
            assertEquals(1, service.myReservations(members.get(0)).size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancellationAndNewBookingCanRunConcurrently() throws Exception {
        var first = service.reserve(members.get(0), stationA, slot);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> cancel = pool.submit(() -> {
                go.await();
                service.cancel(members.get(0), first.reservationId());
                return null;
            });
            Future<EvService.Result> book = pool.submit(() -> {
                go.await();
                return service.reserve(members.get(1), stationA, slot);
            });
            go.countDown();
            cancel.get(15, TimeUnit.SECONDS);
            assertEquals(Outcome.CONFIRMED, book.get(15, TimeUnit.SECONDS).outcome());
            assertEquals(1, service.dayBoard(0, service.findStation(stationA), slot.toLocalDate())
                    .get(slot.getHour()).getReservations().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancellationAllowsSameMemberToReserveElsewhereAtSameTime() {
        var first = service.reserve(members.get(0), stationA, slot);
        assertEquals(Outcome.DUPLICATE, service.reserve(members.get(0), stationB, slot).outcome());
        service.cancel(members.get(0), first.reservationId());
        assertEquals(Outcome.CONFIRMED, service.reserve(members.get(0), stationB, slot).outcome());
    }
}
