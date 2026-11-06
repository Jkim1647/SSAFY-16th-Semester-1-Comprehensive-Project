package com.ssafy.enjoytrip.model.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import com.ssafy.enjoytrip.model.dao.JdbcHotplaceDao;
import com.ssafy.enjoytrip.model.dao.JdbcMemberDao;
import com.ssafy.enjoytrip.model.dao.MemberDao;
import com.ssafy.enjoytrip.model.dto.Member;
import com.ssafy.enjoytrip.model.dto.Page;
import com.ssafy.enjoytrip.model.dto.SearchCondition;
import com.ssafy.enjoytrip.util.Passwords;
import com.ssafy.enjoytrip.util.PhotoStorage;
import com.ssafy.enjoytrip.util.RateLimiter;
import com.ssafy.enjoytrip.util.ResetMailer;

public class BasicMemberService implements MemberService {

    private static final BasicMemberService INSTANCE = new BasicMemberService();
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    /** 같은 오류를 내기 위한 더미 해시: 없는 이메일이어도 해시 계산 시간을 써서 응답 시간으로 회원 존재 여부가 드러나지 않게 한다. */
    private static final String DUMMY_HASH = Passwords.hash("dummy-password-for-timing");

    private static final int RESET_MINUTES = 15;
    private static final RateLimiter EMAIL_LIMIT = new RateLimiter(3, 10 * 60_000);
    private static final RateLimiter IP_LIMIT = new RateLimiter(10, 10 * 60_000);

    private final MemberDao dao = JdbcMemberDao.getInstance();

    private BasicMemberService() {
    }

    public static BasicMemberService getInstance() {
        return INSTANCE;
    }

    @Override
    public Member regist(String email, String name, String password) {
        email = normalizeEmail(email);
        validateName(name);
        validatePassword(password);
        if (dao.findByEmail(email) != null) {
            throw new ValidationException("이미 사용 중인 이메일입니다.");
        }
        Member m = new Member(email, name.trim());
        m.setPasswordHash(Passwords.hash(password));
        m.setMemberId(dao.insert(m));
        return m.withoutSecret();
    }

    @Override
    public boolean isEmailAvailable(String email) {
        try {
            return dao.findByEmail(normalizeEmail(email)) == null;
        } catch (ValidationException e) {
            return false;
        }
    }

    @Override
    public Member login(String email, String password) {
        Member m = email == null ? null : dao.findByEmail(email.trim().toLowerCase());
        if (m == null) {
            Passwords.verify(password == null ? "x" : password, DUMMY_HASH);
            return null;
        }
        if (!Passwords.verify(password, m.getPasswordHash())) {
            return null;
        }
        if (Passwords.needsRehash(m.getPasswordHash())) {
            dao.updatePassword(m.getMemberId(), Passwords.hash(password));
        }
        return m.withoutSecret();
    }

    @Override
    public Member findById(long memberId) {
        Member m = dao.findById(memberId);
        return m == null ? null : m.withoutSecret();
    }

    @Override
    public Page<Member> search(SearchCondition condition) {
        int total = dao.count(condition);
        List<Member> list = dao.search(condition).stream().map(Member::withoutSecret).toList();
        return new Page<>(condition, total, list);
    }

    @Override
    public Member modify(long memberId, String name, String currentPassword, String newPassword) {
        validateName(name);
        Member m = dao.findById(memberId);
        if (m == null) {
            throw new ValidationException("회원 정보를 찾을 수 없습니다.");
        }
        if (newPassword != null && !newPassword.isBlank()) {
            if (!Passwords.verify(currentPassword, m.getPasswordHash())) {
                throw new ValidationException("현재 비밀번호가 맞지 않습니다.");
            }
            validatePassword(newPassword);
            dao.updatePassword(memberId, Passwords.hash(newPassword));
        }
        dao.updateName(memberId, name.trim());
        return findById(memberId);
    }

    @Override
    public void withdraw(long memberId, String password) {
        Member m = dao.findById(memberId);
        if (m == null || !Passwords.verify(password, m.getPasswordHash())) {
            throw new ValidationException("비밀번호가 맞지 않습니다.");
        }
        delete(memberId);
    }

    /** 회원 행을 지우면 핫플·계획·게시글·예약은 CASCADE 로 함께 지워진다. 디스크의 핫플 사진만 따로 정리한다. */
    @Override
    public void delete(long memberId) {
        List<String> photos = JdbcHotplaceDao.getInstance().findPhotoFilesByMember(memberId);
        dao.delete(memberId);
        photos.forEach(PhotoStorage::delete);
    }

    @Override
    public void requestPasswordReset(String email, String name, String clientKey, String resetUrlBase) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        // 같은 이메일은 10분에 3번, 같은 IP 는 10분에 10번까지 (메일 폭탄·무작위 대입 방지)
        if (!EMAIL_LIMIT.tryAcquire(normalized) || !IP_LIMIT.tryAcquire(clientKey)) {
            throw new ValidationException("요청이 너무 많습니다. 잠시 후 다시 시도하세요.");
        }
        Member m = normalized.isEmpty() ? null : dao.findByEmail(normalized);
        if (m == null || name == null || !m.getName().equals(name.trim())) {
            return; // 일치하지 않아도 같은 안내를 보여준다
        }
        String token = Passwords.resetToken();
        dao.saveResetToken(m.getMemberId(), Passwords.sha256Hex(token), LocalDateTime.now().plusMinutes(RESET_MINUTES));
        ResetMailer.send(m.getEmail(), resetUrlBase + token, RESET_MINUTES);
    }

    @Override
    public boolean isResetTokenValid(String token) {
        return token != null && !token.isBlank() && dao.isResetTokenValid(Passwords.sha256Hex(token));
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        validatePassword(newPassword);
        if (token == null || token.isBlank() || !dao.consumeResetToken(Passwords.sha256Hex(token), Passwords.hash(newPassword))) {
            throw new ValidationException("링크가 만료되었거나 이미 사용되었습니다. 비밀번호 찾기를 다시 해 주세요.");
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.length() > 100 || !EMAIL.matcher(email.trim()).matches()) {
            throw new ValidationException("이메일 형식이 올바르지 않습니다.");
        }
        return email.trim().toLowerCase();
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 30) {
            throw new ValidationException("이름은 1~30자로 입력하세요.");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new ValidationException("비밀번호는 8~64자로 입력하세요.");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new ValidationException("비밀번호에는 영문과 숫자가 모두 들어가야 합니다.");
        }
    }
}
