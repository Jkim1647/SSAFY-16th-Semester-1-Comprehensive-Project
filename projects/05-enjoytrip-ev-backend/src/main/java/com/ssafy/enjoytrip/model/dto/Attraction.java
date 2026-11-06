package com.ssafy.enjoytrip.model.dto;

import com.ssafy.enjoytrip.algorithm.geo.Location;

/** 관광지 (한국관광공사 KorService2 areaBasedList2 한 건). */
public class Attraction {

    private int contentId;
    private int contentTypeId;
    private String contentTypeName;
    private String title;
    private String addr1;
    private String addr2;
    private String tel;
    private String firstImage;
    private String firstImage2;
    private double latitude;
    private double longitude;
    private Integer sidoCode;
    private Integer signguCode;
    private String signguName;

    /** 4회차 알고리즘 모듈(경로 최적화)에 넘길 좌표. */
    public Location toLocation() {
        return new Location(String.valueOf(contentId), title, latitude, longitude);
    }

    public int getContentId() {
        return contentId;
    }

    public void setContentId(int contentId) {
        this.contentId = contentId;
    }

    public int getContentTypeId() {
        return contentTypeId;
    }

    public void setContentTypeId(int contentTypeId) {
        this.contentTypeId = contentTypeId;
    }

    public String getContentTypeName() {
        return contentTypeName;
    }

    public void setContentTypeName(String contentTypeName) {
        this.contentTypeName = contentTypeName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAddr1() {
        return addr1;
    }

    public void setAddr1(String addr1) {
        this.addr1 = addr1;
    }

    public String getAddr2() {
        return addr2;
    }

    public void setAddr2(String addr2) {
        this.addr2 = addr2;
    }

    public String getTel() {
        return tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public String getFirstImage() {
        return firstImage;
    }

    public void setFirstImage(String firstImage) {
        this.firstImage = firstImage;
    }

    public String getFirstImage2() {
        return firstImage2;
    }

    public void setFirstImage2(String firstImage2) {
        this.firstImage2 = firstImage2;
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

    public Integer getSidoCode() {
        return sidoCode;
    }

    public void setSidoCode(Integer sidoCode) {
        this.sidoCode = sidoCode;
    }

    public Integer getSignguCode() {
        return signguCode;
    }

    public void setSignguCode(Integer signguCode) {
        this.signguCode = signguCode;
    }

    public String getSignguName() {
        return signguName;
    }

    public void setSignguName(String signguName) {
        this.signguName = signguName;
    }
}
