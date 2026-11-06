package com.ssafy.enjoytrip.model.dto;

import java.time.LocalDate;

/** 여행 계획의 방문지 한 곳 (plan_stops 한 행 + 화면 표시용 관광지 정보). */
public class PlanStop {

    private int seq;
    private int contentId;
    private LocalDate visitDate;
    private int cost;
    private String memo;

    // 아래는 attractions 를 JOIN 해서 채우는 표시용 값 (저장하지 않는다)
    private String title;
    private double latitude;
    private double longitude;
    private String contentTypeName;
    private String addr1;

    public PlanStop() {
    }

    public PlanStop(int contentId, LocalDate visitDate, int cost, String memo) {
        this.contentId = contentId;
        this.visitDate = visitDate;
        this.cost = cost;
        this.memo = memo;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public int getContentId() {
        return contentId;
    }

    public void setContentId(int contentId) {
        this.contentId = contentId;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(LocalDate visitDate) {
        this.visitDate = visitDate;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getContentTypeName() {
        return contentTypeName;
    }

    public void setContentTypeName(String contentTypeName) {
        this.contentTypeName = contentTypeName;
    }

    public String getAddr1() {
        return addr1;
    }

    public void setAddr1(String addr1) {
        this.addr1 = addr1;
    }
}
