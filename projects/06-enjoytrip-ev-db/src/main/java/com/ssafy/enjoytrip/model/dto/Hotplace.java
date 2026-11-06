package com.ssafy.enjoytrip.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 회원이 등록한 핫플레이스 (F105). photoFile 은 업로드 폴더 안의 서버가 만든 파일명(UUID.확장자)이다. */
public class Hotplace {

    private long hotplaceId;
    private long memberId;
    private String writerName;
    private String title;
    private String placeType;
    private LocalDate visitedOn;
    private String description;
    private Double latitude;
    private Double longitude;
    private String photoFile;
    private LocalDateTime createdAt;

    public long getHotplaceId() {
        return hotplaceId;
    }

    public void setHotplaceId(long hotplaceId) {
        this.hotplaceId = hotplaceId;
    }

    public long getMemberId() {
        return memberId;
    }

    public void setMemberId(long memberId) {
        this.memberId = memberId;
    }

    public String getWriterName() {
        return writerName;
    }

    public void setWriterName(String writerName) {
        this.writerName = writerName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPlaceType() {
        return placeType;
    }

    public void setPlaceType(String placeType) {
        this.placeType = placeType;
    }

    public LocalDate getVisitedOn() {
        return visitedOn;
    }

    public void setVisitedOn(LocalDate visitedOn) {
        this.visitedOn = visitedOn;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getPhotoFile() {
        return photoFile;
    }

    public void setPhotoFile(String photoFile) {
        this.photoFile = photoFile;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Hotplace [hotplaceId=" + hotplaceId + ", memberId=" + memberId + ", title=" + title + ", placeType="
                + placeType + ", visitedOn=" + visitedOn + ", photoFile=" + photoFile + "]";
    }
}
