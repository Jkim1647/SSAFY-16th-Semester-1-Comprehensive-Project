package com.ssafy.enjoytrip.model.dto;

import java.time.LocalDateTime;

/** 게시글 (F109 공지사항 · F110 자유게시판). boardType 은 NOTICE | FREE. writerName 은 members 와 JOIN 해서 채운다. */
public class Board {

    public static final String NOTICE = "NOTICE";
    public static final String FREE = "FREE";

    private long boardId;
    private String boardType;
    private long memberId;
    private String writerName;
    private String title;
    private String content;
    private int viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Board() {
    }

    public Board(String boardType, long memberId, String title, String content) {
        this.boardType = boardType;
        this.memberId = memberId;
        this.title = title;
        this.content = content;
    }

    public boolean isNotice() {
        return NOTICE.equals(boardType);
    }

    /** URL·메뉴에 쓰는 소문자 이름 (notice | free) */
    public String getTypeParam() {
        return isNotice() ? "notice" : "free";
    }

    /** 작성 후 수정된 적이 있는지 (상세 화면에 "수정됨" 표시용) */
    public boolean isEdited() {
        return updatedAt != null && createdAt != null && updatedAt.isAfter(createdAt);
    }

    public long getBoardId() {
        return boardId;
    }

    public void setBoardId(long boardId) {
        this.boardId = boardId;
    }

    public String getBoardType() {
        return boardType;
    }

    public void setBoardType(String boardType) {
        this.boardType = boardType;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
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
}
