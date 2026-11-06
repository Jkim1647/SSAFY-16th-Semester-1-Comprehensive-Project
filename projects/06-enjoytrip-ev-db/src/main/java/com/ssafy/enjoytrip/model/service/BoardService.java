package com.ssafy.enjoytrip.model.service;

import java.util.List;

import com.ssafy.enjoytrip.model.dao.BoardDao;
import com.ssafy.enjoytrip.model.dao.JdbcBoardDao;
import com.ssafy.enjoytrip.model.dto.Board;
import com.ssafy.enjoytrip.model.dto.Member;
import com.ssafy.enjoytrip.model.dto.Page;
import com.ssafy.enjoytrip.model.dto.SearchCondition;

/**
 * F109 공지사항 · F110 자유게시판.
 * 권한 규칙: 공지 작성·수정·삭제는 관리자만, 자유글 작성은 로그인 회원, 자유글 수정·삭제는 작성자 본인 또는 관리자.
 */
public class BoardService {

    private static final BoardService INSTANCE = new BoardService();
    public static final int TITLE_MAX = 200;
    public static final int CONTENT_MAX = 10_000;

    private final BoardDao dao = JdbcBoardDao.getInstance();

    private BoardService() {
    }

    public static BoardService getInstance() {
        return INSTANCE;
    }

    public Page<Board> search(String boardType, SearchCondition condition) {
        return new Page<>(condition, dao.count(boardType, condition), dao.search(boardType, condition));
    }

    public List<Board> latestNotices(int limit) {
        return dao.findLatest(Board.NOTICE, limit);
    }

    public Board findById(long boardId) {
        return dao.findById(boardId);
    }

    public void increaseViewCount(long boardId) {
        dao.increaseViewCount(boardId);
    }

    public boolean canWrite(String boardType, Member user) {
        if (user == null) {
            return false;
        }
        return Board.NOTICE.equals(boardType) ? user.isAdmin() : true;
    }

    /** 수정·삭제 권한. 게시판 종류는 요청 파라미터가 아니라 저장된 글의 값으로 판단한다. */
    public boolean canManage(Board board, Member user) {
        if (board == null || user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        return !board.isNotice() && board.getMemberId() == user.getMemberId();
    }

    public long write(String boardType, Member user, String title, String content) {
        if (!canWrite(boardType, user)) {
            throw new ValidationException("글을 쓸 권한이 없습니다.");
        }
        Board b = new Board(boardType, user.getMemberId(), normalizeTitle(title), normalizeContent(content));
        return dao.insert(b);
    }

    public void modify(long boardId, Member user, String title, String content) {
        Board b = dao.findById(boardId);
        if (!canManage(b, user)) {
            throw new ValidationException("수정할 권한이 없습니다.");
        }
        dao.update(boardId, normalizeTitle(title), normalizeContent(content));
    }

    public void delete(long boardId, Member user) {
        Board b = dao.findById(boardId);
        if (!canManage(b, user)) {
            throw new ValidationException("삭제할 권한이 없습니다.");
        }
        dao.delete(boardId);
    }

    private static String normalizeTitle(String title) {
        String t = title == null ? "" : title.strip();
        if (t.isEmpty() || t.length() > TITLE_MAX) {
            throw new ValidationException("제목은 1~" + TITLE_MAX + "자로 입력하세요.");
        }
        return t;
    }

    private static String normalizeContent(String content) {
        // 줄바꿈은 화면에서 pre-wrap 으로 그대로 보여주므로 앞뒤 공백만 정리하고 \r\n 은 \n 으로 맞춘다
        String c = content == null ? "" : content.replace("\r\n", "\n").strip();
        if (c.isEmpty() || c.length() > CONTENT_MAX) {
            throw new ValidationException("내용은 1~" + String.format("%,d", CONTENT_MAX) + "자로 입력하세요.");
        }
        return c;
    }
}
