package com.ssafy.enjoytrip.model.dto;

import java.time.LocalDateTime;

/** 지난 시뮬레이션 실행 기록 한 줄 (LOCKED vs NAIVE 비교 표). */
public class SimRunSummary {

    private long runId;
    private String mode;
    private String queueType;
    private int vehicles;
    private int stations;
    private String status;
    private int confirmed;
    private int rejected;
    private int overbooked;
    private LocalDateTime startedAt;
    private Long durationMs;

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getQueueType() {
        return queueType;
    }

    public void setQueueType(String queueType) {
        this.queueType = queueType;
    }

    public int getVehicles() {
        return vehicles;
    }

    public void setVehicles(int vehicles) {
        this.vehicles = vehicles;
    }

    public int getStations() {
        return stations;
    }

    public void setStations(int stations) {
        this.stations = stations;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(int confirmed) {
        this.confirmed = confirmed;
    }

    public int getRejected() {
        return rejected;
    }

    public void setRejected(int rejected) {
        this.rejected = rejected;
    }

    public int getOverbooked() {
        return overbooked;
    }

    public void setOverbooked(int overbooked) {
        this.overbooked = overbooked;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}
