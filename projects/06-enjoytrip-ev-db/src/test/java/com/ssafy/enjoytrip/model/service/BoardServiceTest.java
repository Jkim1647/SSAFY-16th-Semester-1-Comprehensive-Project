package com.ssafy.enjoytrip.model.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ssafy.enjoytrip.model.dto.Board;
import com.ssafy.enjoytrip.model.dto.Member;

/** 게시판 권한 규칙 (DB 없이 확인할 수 있는 부분) */
class BoardServiceTest {

    private final BoardService service = BoardService.getInstance();

    private static Member member(long id, String role) {
        Member m = new Member("u" + id + "@test.kr", "회원" + id);
        m.setMemberId(id);
        m.setRole(role);
        return m;
    }

    private final Member admin = member(1, Member.ROLE_ADMIN);
    private final Member writer = member(2, Member.ROLE_USER);
    private final Member other = member(3, Member.ROLE_USER);

    @Test
    void onlyAdminWritesNotice() {
        assertTrue(service.canWrite(Board.NOTICE, admin));
        assertFalse(service.canWrite(Board.NOTICE, writer));
        assertTrue(service.canWrite(Board.FREE, writer));
        assertFalse(service.canWrite(Board.FREE, null));
    }

    @Test
    void freePostManagedByWriterOrAdmin() {
        Board post = new Board(Board.FREE, writer.getMemberId(), "t", "c");
        assertTrue(service.canManage(post, writer));
        assertTrue(service.canManage(post, admin));
        assertFalse(service.canManage(post, other));
        assertFalse(service.canManage(post, null));
    }

    @Test
    void noticeManagedByAdminOnlyEvenIfUserIsRecordedWriter() {
        Board notice = new Board(Board.NOTICE, writer.getMemberId(), "t", "c");
        assertFalse(service.canManage(notice, writer));
        assertTrue(service.canManage(notice, admin));
    }

    @Test
    void rejectsBlankOrTooLongInput() {
        assertThrows(ValidationException.class,
                () -> service.write(Board.FREE, writer, "   ", "본문"));
        assertThrows(ValidationException.class,
                () -> service.write(Board.FREE, writer, "a".repeat(201), "본문"));
        assertThrows(ValidationException.class,
                () -> service.write(Board.FREE, writer, "제목", "b".repeat(10_001)));
        assertThrows(ValidationException.class,
                () -> service.write(Board.NOTICE, writer, "제목", "본문"));
    }
}
