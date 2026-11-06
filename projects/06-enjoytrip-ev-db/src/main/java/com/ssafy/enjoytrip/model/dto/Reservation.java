package com.ssafy.enjoytrip.model.dto;

import java.time.LocalDateTime;

/** 충전 예약 한 건. runId 0 = 실제 회원 예약, 1 이상 = 시뮬레이션. */
public class Reservation {

    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELED = "CANCELED";

    private long reservationId;
    private long runId;
    private String statId;
    private String stationName;
    private LocalDateTime slotStart;
    private int chargerNo;
    private Long memberId;
    private Integer vehicleNo;
    private String status = CONFIRMED;
    private LocalDateTime createdAt;

    public long getReservationId() {
        return reservationId;
    }

    public void setReservationId(long reservationId) {
        this.reservationId = reservationId;
    }

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public String getStatId() {
        return statId;
    }

    public void setStatId(String statId) {
        this.statId = statId;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public LocalDateTime getSlotStart() {
        return slotStart;
    }

    public void setSlotStart(LocalDateTime slotStart) {
        this.slotStart = slotStart;
    }

    public int getChargerNo() {
        return chargerNo;
    }

    public void setChargerNo(int chargerNo) {
        this.chargerNo = chargerNo;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Integer getVehicleNo() {
        return vehicleNo;
    }

    public void setVehicleNo(Integer vehicleNo) {
        this.vehicleNo = vehicleNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** JSP 에서 "10/31 19:00" 처럼 보여주기 위한 값. */
    public String getSlotLabel() {
        return slotStart == null ? "" : String.format("%d/%d %02d:00", slotStart.getMonthValue(), slotStart.getDayOfMonth(), slotStart.getHour());
    }
}
