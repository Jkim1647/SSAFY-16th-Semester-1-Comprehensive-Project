package com.ssafy.enjoytrip.model.service;

import com.ssafy.enjoytrip.model.dto.Member;
import com.ssafy.enjoytrip.model.dto.Page;
import com.ssafy.enjoytrip.model.dto.SearchCondition;

public interface MemberService {

    /** 회원가입. 규칙 위반·이메일 중복은 ValidationException. */
    Member regist(String email, String name, String password);

    boolean isEmailAvailable(String email);

    /** 성공하면 비밀번호 해시를 뺀 회원, 실패하면 null. */
    Member login(String email, String password);

    Member findById(long memberId);

    Page<Member> search(SearchCondition condition);

    /** 이름 변경, newPassword 가 비어 있지 않으면 currentPassword 확인 후 비밀번호도 변경. */
    Member modify(long memberId, String name, String currentPassword, String newPassword);

    /** 본인 탈퇴: 비밀번호 확인 후 삭제. */
    void withdraw(long memberId, String password);

    /** 관리자 강제 탈퇴. */
    void delete(long memberId);

    /**
     * 비밀번호 재설정 요청. 이메일·이름이 맞으면 15분짜리 1회용 링크를 만들어 "보낸다".
     * 맞든 틀리든 결과를 돌려주지 않아 회원 존재 여부가 드러나지 않는다. 기존 비밀번호는 링크로 재설정할 때까지 그대로다.
     *
     * @param clientKey 요청 IP (요청 횟수 제한용)
     * @param resetUrlBase 예: http://localhost:8090/enjoytrip/member?action=reset-form&token=
     */
    void requestPasswordReset(String email, String name, String clientKey, String resetUrlBase);

    boolean isResetTokenValid(String token);

    /** 링크의 토큰으로 새 비밀번호를 정한다. 토큰이 만료·사용됐거나 규칙 위반이면 ValidationException. */
    void resetPassword(String token, String newPassword);
}
