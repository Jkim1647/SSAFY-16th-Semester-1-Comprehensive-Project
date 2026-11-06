package com.ssafy.enjoytrip.model.dto;

public class BoardEntry extends Board {
    private String category = "GENERAL";
    public String getCategory() {
        return category;
    }
    public void setCategory(String value) {
        category = value;
    }
    private boolean hidden = false;
    public boolean isHidden() {
        return hidden;
    }
    public void setHidden(boolean value) {
        hidden = value;
    }
    private boolean pinned = false;
    public boolean isPinned() {
        return pinned;
    }
    public void setPinned(boolean value) {
        pinned = value;
    }
    private int pinOrder = 0;
    public int getPinOrder() {
        return pinOrder;
    }
    public void setPinOrder(int value) {
        pinOrder = value;
    }
    private java.time.LocalDateTime pinStart = null;
    public java.time.LocalDateTime getPinStart() {
        return pinStart;
    }
    public void setPinStart(java.time.LocalDateTime value) {
        pinStart = value;
    }
    private java.time.LocalDateTime pinEnd = null;
    public java.time.LocalDateTime getPinEnd() {
        return pinEnd;
    }
    public void setPinEnd(java.time.LocalDateTime value) {
        pinEnd = value;
    }
    private int likeCount = 0;
    public int getLikeCount() {
        return likeCount;
    }
    public void setLikeCount(int value) {
        likeCount = value;
    }
    private int commentCount = 0;
    public int getCommentCount() {
        return commentCount;
    }
    public void setCommentCount(int value) {
        commentCount = value;
    }
    private boolean liked = false;
    public boolean isLiked() {
        return liked;
    }
    public void setLiked(boolean value) {
        liked = value;
    }
    private boolean bookmarked = false;
    public boolean isBookmarked() {
        return bookmarked;
    }
    public void setBookmarked(boolean value) {
        bookmarked = value;
    }
}
