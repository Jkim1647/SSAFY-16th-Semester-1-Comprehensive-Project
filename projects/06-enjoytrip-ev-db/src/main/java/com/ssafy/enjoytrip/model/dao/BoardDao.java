package com.ssafy.enjoytrip.model.dao;

import java.util.List;

import com.ssafy.enjoytrip.model.dto.Board;
import com.ssafy.enjoytrip.model.dto.SearchCondition;

public interface BoardDao {

    long insert(Board board);

    Board findById(long boardId);

    int count(String boardType, SearchCondition condition);

    List<Board> search(String boardType, SearchCondition condition);

    /** 최신 글 limit 건 (자유게시판 위에 고정할 공지용) */
    List<Board> findLatest(String boardType, int limit);

    int update(long boardId, String title, String content);

    int increaseViewCount(long boardId);

    int delete(long boardId);
}
