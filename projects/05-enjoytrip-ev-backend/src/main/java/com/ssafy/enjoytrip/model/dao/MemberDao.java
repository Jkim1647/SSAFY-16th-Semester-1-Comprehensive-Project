package com.ssafy.enjoytrip.model.dao;

import java.time.LocalDateTime;
import java.util.List;

import com.ssafy.enjoytrip.model.dto.Member;
import com.ssafy.enjoytrip.model.dto.SearchCondition;

/** 회원 데이터 접근. 구현체만 바꾸면(File → JDBC) 서비스·컨트롤러는 그대로다. */
public interface MemberDao {

    /** @return 생성된 member_id */
    long insert(Member member);

    Member findByEmail(String email);

    Member findById(long memberId);

    int count(SearchCondition condition);

    List<Member> search(SearchCondition condition);

    int updateName(long memberId, String name);

    int updatePassword(long memberId, String passwordHash);

    int delete(long memberId);

    /** 회원의 이전 재설정 토큰을 지우고 새 토큰(해시)을 저장한다. */
    void saveResetToken(long memberId, String tokenHash, LocalDateTime expiresAt);

    /**
     * 유효한(만료 전·미사용) 토큰이면 사용 처리하고 새 비밀번호 해시로 바꾼 뒤 true. 한 트랜잭션에서 처리해
     * 같은 링크를 두 번 눌러도 한 번만 성공한다.
     */
    boolean consumeResetToken(String tokenHash, String newPasswordHash);

    /** 토큰이 아직 쓸 수 있는지만 확인 (재설정 화면을 보여줄지 결정). */
    boolean isResetTokenValid(String tokenHash);
}
