package com.ssafy.enjoytrip.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 여행 계획 (F104). stops 는 방문 순서(seq)대로 담긴다. */
public class Plan {

    public static final String NO_DATE = "날짜 미정";
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private long planId;
    private long memberId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private int startSoc = 80;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PlanStop> stops = new ArrayList<>();

    // 목록 화면에서는 stops 를 읽지 않고 DB 에서 집계한 값만 채운다
    private int stopCount;
    private int totalCost;

    /** 일자별 경비 합계 (날짜순, 날짜를 정하지 않은 방문지는 맨 뒤 "날짜 미정"). */
    public Map<String, Integer> getDailyCosts() {
        TreeMap<LocalDate, Integer> byDate = new TreeMap<>();
        int noDate = 0;
        boolean hasNoDate = false;
        for (PlanStop s : stops) {
            if (s.getVisitDate() == null) {
                noDate += s.getCost();
                hasNoDate = true;
            } else {
                byDate.merge(s.getVisitDate(), s.getCost(), Integer::sum);
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        byDate.forEach((date, cost) -> result.put(date.toString(), cost));
        if (hasNoDate) {
            result.put(NO_DATE, noDate);
        }
        return result;
    }

    /** 여행 일수 (시작·종료일이 모두 있을 때만, 당일치기 = 1). */
    public Integer getDays() {
        if (startDate == null || endDate == null) {
            return null;
        }
        return (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
    }

    public String getUpdatedLabel() {
        return updatedAt == null ? "" : updatedAt.format(LABEL);
    }

    public long getPlanId() {
        return planId;
    }

    public void setPlanId(long planId) {
        this.planId = planId;
    }

    public long getMemberId() {
        return memberId;
    }

    public void setMemberId(long memberId) {
        this.memberId = memberId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getStartSoc() {
        return startSoc;
    }

    public void setStartSoc(int startSoc) {
        this.startSoc = startSoc;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PlanStop> getStops() {
        return stops;
    }

    public void setStops(List<PlanStop> stops) {
        this.stops = stops == null ? new ArrayList<>() : stops;
    }

    public int getStopCount() {
        return stopCount;
    }

    public void setStopCount(int stopCount) {
        this.stopCount = stopCount;
    }

    public int getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(int totalCost) {
        this.totalCost = totalCost;
    }
}
