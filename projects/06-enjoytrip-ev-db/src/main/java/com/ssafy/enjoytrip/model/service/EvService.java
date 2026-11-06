package com.ssafy.enjoytrip.model.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.ssafy.enjoytrip.model.dao.EvDao;
import com.ssafy.enjoytrip.model.dao.JdbcEvDao;
import com.ssafy.enjoytrip.model.dto.EvStation;
import com.ssafy.enjoytrip.model.dto.Reservation;
import com.ssafy.enjoytrip.model.dto.SlotStatus;
import com.ssafy.enjoytrip.util.DBUtil;
import com.ssafy.enjoytrip.util.DataAccessException;

/**
 * 충전소 조회와 예약. 예약이 몰려도 한 슬롯에 충전기 수보다 많은 예약이 확정되지 않게 하는 것이 핵심 규칙이다.
 *
 * <ul>
 *   <li>LOCKED: 트랜잭션 안에서 슬롯 행을 SELECT ... FOR UPDATE 로 잠그고 남은 자리를 확인한 뒤 예약 → 오버부킹 0.</li>
 *   <li>NAIVE: 잠금 없이 "세고 → 넣기". 세는 시점과 넣는 시점 사이에 다른 요청이 끼어들어 정원을 넘긴다.
 *       시뮬레이션에서 LOCKED 와 비교해 보여주기 위해서만 쓴다.</li>
 * </ul>
 */
public class EvService {

    public enum Mode { LOCKED, NAIVE }

    public enum Outcome { CONFIRMED, FULL, DUPLICATE }

    /** 예약 시도 결과. chargerNo 는 확정됐을 때 배정된 충전기 번호(좌석 번호). */
    public record Result(Outcome outcome, int chargerNo, long reservationId) {
        static Result full() {
            return new Result(Outcome.FULL, 0, 0);
        }
    }

    private static final EvService INSTANCE = new EvService();
    private static final int MAX_RETRY = 3;
    public static final int BOOKING_DAYS = 7;

    private final EvDao dao = JdbcEvDao.getInstance();

    private EvService() {
    }

    public static EvService getInstance() {
        return INSTANCE;
    }

    public EvStation findStation(String statId) {
        return dao.findStation(statId);
    }

    public List<EvStation> nearby(double lat, double lng, double radiusKm, int minKw, int limit) {
        return dao.findNearby(lat, lng, Math.min(radiusKm, 50), minKw, Math.min(limit, 500));
    }

    public List<EvStation> searchByName(String keyword, int limit) {
        return dao.searchByName(keyword, Math.min(limit, 100));
    }

    public List<EvStation> allStations(int minKw) {
        return dao.findAll(minKw);
    }

    public List<Reservation> myReservations(long memberId) {
        return dao.findByMember(memberId);
    }

    /** 하루치 좌석 배치도: 0시~23시 슬롯마다 충전기 수(capacity)와 확정 예약. */
    public List<SlotStatus> dayBoard(long runId, EvStation station, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        List<SlotStatus> slots = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            slots.add(new SlotStatus(from.plusHours(h), station.getChargerCount()));
        }
        for (Reservation r : dao.findConfirmed(runId, station.getStatId(), from, from.plusDays(1))) {
            slots.get(r.getSlotStart().getHour()).getReservations().add(r);
        }
        return slots;
    }

    /** 회원 예약 (항상 LOCKED). */
    public Result reserve(long memberId, String statId, LocalDateTime slot) {
        EvStation station = dao.findStation(statId);
        if (station == null) {
            throw new ValidationException("충전소를 찾을 수 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (slot.getMinute() != 0 || slot.getSecond() != 0 || !slot.isAfter(now)
                || slot.isAfter(now.toLocalDate().plusDays(BOOKING_DAYS).atStartOfDay())) {
            throw new ValidationException("예약은 지금 이후부터 " + BOOKING_DAYS + "일 안의 정각 슬롯만 가능합니다.");
        }
        return tryReserve(0, station, slot, memberId, null, Mode.LOCKED);
    }

    /** 예약 취소: 본인 예약만, 아직 시작 전인 것만. 슬롯의 booked 를 함께 되돌린다. */
    public void cancel(long memberId, long reservationId) {
        Connection con = null;
        try {
            con = DBUtil.getConnection();
            con.setAutoCommit(false);
            dao.lockMember(con, memberId);
            Reservation r = dao.findReservationForUpdate(con, reservationId);
            if (r == null || r.getRunId() != 0 || r.getMemberId() == null || r.getMemberId() != memberId) {
                throw new ValidationException("취소할 수 있는 예약이 아닙니다.");
            }
            if (!Reservation.CONFIRMED.equals(r.getStatus()) || !r.getSlotStart().isAfter(LocalDateTime.now())) {
                throw new ValidationException("이미 취소했거나 시작된 예약입니다.");
            }
            dao.lockSlot(con, 0, r.getStatId(), r.getSlotStart());
            dao.updateStatus(con, reservationId, Reservation.CANCELED);
            dao.increaseBooked(con, 0, r.getStatId(), r.getSlotStart(), -1);
            con.commit();
        } catch (SQLException e) {
            DBUtil.rollback(con);
            throw new DataAccessException("예약 취소 실패", e);
        } catch (RuntimeException e) {
            DBUtil.rollback(con);
            throw e;
        } finally {
            close(con);
        }
    }

    /**
     * 예약 한 건 시도. 시뮬레이션의 가상 차량과 회원 예약이 같은 경로를 쓴다.
     * 교착 상태·잠금 대기 초과(일시적 오류)는 최대 3번 다시 시도한다.
     */
    public Result tryReserve(long runId, EvStation station, LocalDateTime slot, Long memberId, Integer vehicleNo, Mode mode) {
        for (int attempt = 1;; attempt++) {
            try {
                return mode == Mode.LOCKED
                        ? reserveLocked(runId, station, slot, memberId, vehicleNo)
                        : reserveNaive(runId, station, slot, memberId, vehicleNo);
            } catch (SQLTransientException e) {
                if (attempt >= MAX_RETRY) {
                    throw new DataAccessException("예약이 몰려 처리하지 못했습니다. 잠시 후 다시 시도하세요.", e);
                }
                sleepQuietly(ThreadLocalRandom.current().nextInt(5, 20) * attempt);
            } catch (SQLException e) {
                throw new DataAccessException("예약 처리 실패", e);
            }
        }
    }

    private Result reserveLocked(long runId, EvStation station, LocalDateTime slot, Long memberId, Integer vehicleNo)
            throws SQLException {
        Connection con = null;
        try {
            con = DBUtil.getConnection();
            dao.ensureSlot(con, runId, station.getStatId(), slot, station.getChargerCount());
            con.setAutoCommit(false);
            // 회원 -> 슬롯 순으로 잠근다. 서로 다른 충전소에 대한 중복 요청도 직렬화한다.
            if (memberId != null) {
                dao.lockMember(con, memberId);
            }
            int[] slotRow = dao.lockSlot(con, runId, station.getStatId(), slot); // 여기서 같은 슬롯 요청이 줄을 선다
            int capacity = slotRow[0];
            int booked = slotRow[1];
            if (memberId != null && dao.hasMemberReservation(con, memberId, slot)) {
                con.rollback();
                return new Result(Outcome.DUPLICATE, 0, 0);
            }
            if (booked >= capacity) {
                con.rollback();
                return Result.full();
            }
            int chargerNo = dao.findAvailableCharger(con, runId, station.getStatId(), slot, capacity);
            if (chargerNo == 0) {
                con.rollback();
                return Result.full();
            }
            Reservation r = newReservation(runId, station, slot, chargerNo, memberId, vehicleNo);
            long id = dao.insertReservation(con, r);
            dao.increaseBooked(con, runId, station.getStatId(), slot, 1);
            con.commit();
            return new Result(Outcome.CONFIRMED, chargerNo, id);
        } catch (SQLException | RuntimeException e) {
            DBUtil.rollback(con);
            throw e;
        } finally {
            close(con);
        }
    }

    private Result reserveNaive(long runId, EvStation station, LocalDateTime slot, Long memberId, Integer vehicleNo)
            throws SQLException {
        try (Connection con = DBUtil.getConnection()) {
            dao.ensureSlot(con, runId, station.getStatId(), slot, station.getChargerCount());
            int booked = dao.countConfirmed(con, runId, station.getStatId(), slot);
            if (booked >= station.getChargerCount()) {
                return Result.full();
            }
            // 실제 서비스라면 결제 승인·차량 검증 같은 처리가 이 사이에 들어간다. 그 몇 ms 동안 다른 요청도 "자리 있음"을 본다.
            sleepQuietly(ThreadLocalRandom.current().nextInt(3, 11));
            long id = dao.insertReservation(con, newReservation(runId, station, slot, booked + 1, memberId, vehicleNo));
            return new Result(Outcome.CONFIRMED, booked + 1, id);
        }
    }

    private static Reservation newReservation(long runId, EvStation station, LocalDateTime slot, int chargerNo,
            Long memberId, Integer vehicleNo) {
        Reservation r = new Reservation();
        r.setRunId(runId);
        r.setStatId(station.getStatId());
        r.setSlotStart(slot);
        r.setChargerNo(chargerNo);
        r.setMemberId(memberId);
        r.setVehicleNo(vehicleNo);
        return r;
    }

    public int countOverbooked(long runId) {
        return dao.countOverbooked(runId);
    }

    public void purgeOldRuns(int keep) {
        dao.purgeOldRuns(keep);
    }

    private static void close(Connection con) {
        if (con == null) {
            return;
        }
        try {
            con.setAutoCommit(true);
            con.close();
        } catch (SQLException e) {
            System.err.println("연결 반납 실패: " + e.getMessage());
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
