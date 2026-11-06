package com.ssafy.enjoytrip.model.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 회원. passwordHash 는 DAO·서비스 안에서만 쓰고 세션·JSON 으로 내보내지 않는다(withoutSecret). */
public class Member implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    private long memberId;
    private String email;
    private String name;
    private transient String passwordHash;
    private String role = ROLE_USER;
    private LocalDateTime createdAt;

    public Member() {
    }

    public Member(String email, String name) {
        this.email = email;
        this.name = name;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public Member withoutSecret() {
        Member m = new Member(email, name);
        m.memberId = memberId;
        m.role = role;
        m.createdAt = createdAt;
        return m;
    }

    public long getMemberId() {
        return memberId;
    }

    public void setMemberId(long memberId) {
        this.memberId = memberId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Member [memberId=" + memberId + ", email=" + email + ", name=" + name + ", role=" + role + "]";
    }
}
