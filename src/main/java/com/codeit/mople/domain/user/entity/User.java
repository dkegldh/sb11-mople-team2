package com.codeit.mople.domain.user.entity;

import com.codeit.mople.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(name = "uq_users_email", columnNames = "email")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private String name;

  private String profileImageUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(nullable = false)
  private boolean locked;

  @Column
  private String temporaryPassword;

  @Column
  private Instant temporaryPasswordExpiresAt;

  @Column
  private String refreshToken;

  @Column
  private Instant refreshTokenExpireAt;

  /**
   * TODO: Phase5 — Redis 기반 세션 관리로 전환 시 이 컬럼 제거 예정
   * (Redis에 "현재 유효한 토큰"을 저장하는 방식으로 대체)
   */
  @Column(nullable = false)
  private long sessionVersion = 0L;

  private User(String email, String password, String name, Role role) {
    this.email = Objects.requireNonNull(email, "email");
    this.password = Objects.requireNonNull(password, "password");
    this.name = Objects.requireNonNull(name, "name");
    this.role = Objects.requireNonNull(role, "role");
    this.locked = false;
  }

  public static User createUser(String email, String password, String name) {
    return new User(email, password, name, Role.USER);
  }
//어드민 계정 자동으로 초기화
  public static User createAdmin(String email, String password, String name) {
    return new User(email, password, name, Role.ADMIN);
  }

  // 프로필 변경
  public void updateProfile(String name, String profileImageUrl) {
    if(name != null) {
      this.name = name;
    }
    if(profileImageUrl != null) {
      this.profileImageUrl = profileImageUrl;
    }
  }

  // 비밀번호 변경
  public void changePassword(String encodedNewPassword) {
    this.password = Objects.requireNonNull(encodedNewPassword, "encodedNewPassword");
  }

  // 어드민 기능
  public void changeRole(Role role) {
    this.role = Objects.requireNonNull(role, "role");
  }

  /**
   * 로그인 시 호출하여 세션 버전을 증가시킵니다.
   * TODO: Phase5 — Redis 기반 세션 관리로 전환 시 이 필드/메서드 제거 예정
   *
   * @return 증가된 이후의 세션 버전 값
   */
  public long increaseSessionVersion() {
    return ++this.sessionVersion;
  }

  public void lock() {
    this.locked = true;
  }

  public void unlock() {
    this.locked = false;
  }

  public void issueTemporaryPassword(String encodedTemporaryPassword, Instant expiresAt) {
    this.temporaryPassword = Objects.requireNonNull(encodedTemporaryPassword, "encodedTemporaryPassword");
    this.temporaryPasswordExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
  }

  public void destroyTemporaryPassword() {
    this.temporaryPassword = null;
    this.temporaryPasswordExpiresAt = null;
  }

  public boolean hasValidTemporaryPassword(Instant now) {
    return temporaryPassword != null && temporaryPasswordExpiresAt != null && now.isBefore(temporaryPasswordExpiresAt);
  }

  public void updateRefreshToken(String refreshToken, Instant expiresAt) {
    this.refreshToken = Objects.requireNonNull(refreshToken, "refreshToken");
    this.refreshTokenExpireAt = Objects.requireNonNull(expiresAt, "expiresAt");
  }

  public boolean isRefreshTokenValid(String refreshToken, Instant now) {
    return this.refreshToken != null
        && this.refreshToken.equals(refreshToken)
        && this.refreshTokenExpireAt != null
        && now.isBefore(this.refreshTokenExpireAt);
  }

  public void clearRefreshToken() {
    this.refreshToken = null;
    this.refreshTokenExpireAt = null;
  }
}
