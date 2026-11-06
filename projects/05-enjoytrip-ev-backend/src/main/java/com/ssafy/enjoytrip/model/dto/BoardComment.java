package com.ssafy.enjoytrip.model.dto;

public class BoardComment {
    private int page = 1;
    public int getPage() {
        return page;
    }
    public void setPage(int value) {
        page = value;
    }
    private long commentId = 0;
    public long getCommentId() {
        return commentId;
    }
    public void setCommentId(long value) {
        commentId = value;
    }
    private long boardId = 0;
    public long getBoardId() {
        return boardId;
    }
    public void setBoardId(long value) {
        boardId = value;
    }
    private long memberId = 0;
    public long getMemberId() {
        return memberId;
    }
    public void setMemberId(long value) {
        memberId = value;
    }
    private String writerName = null;
    public String getWriterName() {
        return writerName;
    }
    public void setWriterName(String value) {
        writerName = value;
    }
    private String boardTitle = null;
    public String getBoardTitle() {
        return boardTitle;
    }
    public void setBoardTitle(String value) {
        boardTitle = value;
    }
    private String content = null;
    public String getContent() {
        return content;
    }
    public void setContent(String value) {
        content = value;
    }
    private boolean hidden = false;
    public boolean isHidden() {
        return hidden;
    }
    public void setHidden(boolean value) {
        hidden = value;
    }
    private java.time.LocalDateTime createdAt = null;
    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(java.time.LocalDateTime value) {
        createdAt = value;
    }
    private java.time.LocalDateTime updatedAt = null;
    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(java.time.LocalDateTime value) {
        updatedAt = value;
    }
}
