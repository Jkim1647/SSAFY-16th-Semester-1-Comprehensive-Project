package com.ssafy.enjoytrip.model.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 충전소 한 곳의 1시간 슬롯 좌석 현황 (티켓팅 좌석 배치도 한 줄). */
public class SlotStatus {

    private final LocalDateTime slotStart;
    private final int capacity;
    /** 확정된 예약들. 정상이라면 size ≤ capacity, 넘치면 오버부킹. */
    private final List<Reservation> reservations = new ArrayList<>();

    public SlotStatus(LocalDateTime slotStart, int capacity) {
        this.slotStart = slotStart;
        this.capacity = capacity;
    }

    public LocalDateTime getSlotStart() {
        return slotStart;
    }

    public String getLabel() {
        return String.format("%02d:00", slotStart.getHour());
    }

    public int getCapacity() {
        return capacity;
    }

    public List<Reservation> getReservations() {
        return reservations;
    }

    public int getBooked() {
        return reservations.size();
    }

    public int getRemaining() {
        return Math.max(0, capacity - reservations.size());
    }

    public boolean isFull() {
        return reservations.size() >= capacity;
    }

    public boolean isPast() {
        return slotStart.isBefore(LocalDateTime.now());
    }
}
