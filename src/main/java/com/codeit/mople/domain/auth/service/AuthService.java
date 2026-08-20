package com.codeit.mople.domain.auth.service;

import com.codeit.mople.domain.auth.dto.request.ResetPasswordRequest;
import com.codeit.mople.domain.auth.dto.request.SignInRequest;
import com.codeit.mople.domain.auth.dto.response.AuthTokens;
import com.codeit.mople.domain.auth.dto.response.RefreshToken;
import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.auth.repository.PasswordResetRateLimiterRepository;
import com.codeit.mople.domain.auth.repository.RefreshTokenRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.user.dto.response.UserDto;
import com.codeit.mople.domain.user.entity.AuthProvider;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import io.jsonwebtoken.JwtException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final String TEMPORARY_PASSWORD_CHARS =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
  private static final int TEMPORARY_PASSWORD_LENGTH = 16;
  private static final long TEMPORARY_PASSWORD_EXPIRATION_MINUTES = 3L;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  private final SessionTokenRepository sessionTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthMailService authMailService;
  private final PasswordResetRateLimiterRepository passwordResetRateLimiterRepository;

  @Value("${password-reset.rate-limit.ip.max-requests:5}")
  private int ipMaxRequests;

  @Value("${password-reset.rate-limit.ip.window-minutes:60}")
  private long ipWindowMinutes;

  @Value("${password-reset.rate-limit.global.max-requests:200}")
  private int globalMaxRequests;

  @Value("${password-reset.rate-limit.global.window-minutes:1440}")
  private long globalWindowMinutes;

  @Transactional
  public AuthTokens signIn(SignInRequest request) {
    User user = userRepository.findByEmail(request.username().toLowerCase(Locale.ROOT))
        .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));

    if(!isPasswordValid(request.password(), user)) {
      throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    if(user.isLocked()) {
      throw new AuthException(AuthErrorCode.LOCKED_ACCOUNT);
    }

    String jti = UUID.randomUUID().toString();
    String accessToken = jwtProvider.createAccessToken(user.getId(), jti, user.getRole());
    sessionTokenRepository.save(user.getId(), jti, sessionTtl());

    return issueRefreshToken(user, accessToken);
  }

  @Transactional
  public RefreshToken issueOAuthRefreshToken(UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalStateException("OAuth 로그인 후 사용자를 찾을 수 없습니다."));

    String refreshToken = jwtProvider.createRefreshToken(user.getId());
    Instant refreshExpiresAt = Instant.now().plusMillis(jwtProvider.getRefreshTokenExpiration());
    refreshTokenRepository.save(user.getId(), refreshToken, sessionTtl());

    return new RefreshToken(refreshToken, refreshExpiresAt);
  }

  private boolean isPasswordValid(String rawPassword, User user) {
    // 소셜 계정은 password가 null이라 matches 호출 자체가 위험
    if(user.getProvider() != AuthProvider.LOCAL) {
      return false;
    }
    if(passwordEncoder.matches(rawPassword, user.getPassword())) {
      return true;
    }
    return user.hasValidTemporaryPassword(Instant.now())
        && passwordEncoder.matches(rawPassword, user.getTemporaryPassword());
  }

  @Transactional
  public void resetPassword(ResetPasswordRequest request, String clientIp) {
    String email = request.email().toLowerCase(Locale.ROOT);

    if(!passwordResetRateLimiterRepository.tryAcquireGlobal(
        globalMaxRequests, Duration.ofMinutes(globalWindowMinutes))) {
      log.warn("비밀번호 재설정 전체 발송 한도 초과");
      throw new AuthException(AuthErrorCode.TOO_MANY_RESET_PASSWORD_REQUEST);
    }

    if(!passwordResetRateLimiterRepository.tryAcquireByIp(
        clientIp, ipMaxRequests, Duration.ofMinutes(ipWindowMinutes))) {
      log.warn("비밀번호 재설정 IP 기준 요청 한도 초과 : {}", clientIp);
      throw new AuthException(AuthErrorCode.TOO_MANY_RESET_PASSWORD_REQUEST);
    }

    if(!passwordResetRateLimiterRepository.tryAcquireByEmail(email, Duration.ofMinutes(TEMPORARY_PASSWORD_EXPIRATION_MINUTES))) {
      throw new AuthException(AuthErrorCode.TOO_MANY_RESET_PASSWORD_REQUEST);
    }

    userRepository.findByEmail(email)
        .filter(user -> user.getProvider() == AuthProvider.LOCAL)
        .ifPresent(user -> {
          String temporaryPassword = generateTemporaryPassword();
          Instant expiresAt = Instant.now().plus(TEMPORARY_PASSWORD_EXPIRATION_MINUTES, ChronoUnit.MINUTES);
          user.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword), expiresAt);
          authMailService.sendTemporaryPassword(user.getEmail(), temporaryPassword);
        });
    // 이메일이 있든 없든 항상 204 반환(가입 여부를 노출하지 않음)
  }

  private String generateTemporaryPassword() {
    StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
    for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
      int index = SECURE_RANDOM.nextInt(TEMPORARY_PASSWORD_CHARS.length());
      password.append(TEMPORARY_PASSWORD_CHARS.charAt(index));
    }
    return password.toString();
  }

  @Transactional
  public void signOut(String refreshToken) {
    if(refreshToken == null) {
      return;
    }

    UUID userId;
    try {
      userId = jwtProvider.getUserId(refreshToken);
    } catch (JwtException | IllegalArgumentException e) {
      return;
    }

    if(!refreshTokenRepository.isValid(userId, refreshToken)) {
      return;
    }
    refreshTokenRepository.invalidate(userId);
    sessionTokenRepository.invalidate(userId);
  }

  @Transactional
  public AuthTokens refresh(String refreshToken) {
    UUID userId;
    try {
      userId = jwtProvider.getUserId(refreshToken);
    } catch (JwtException | IllegalArgumentException e) {
      throw new AuthException(AuthErrorCode.INVALID_TOKEN);
    }

    String newRefreshToken = jwtProvider.createRefreshToken(userId);
    if(!refreshTokenRepository.rotate(userId, refreshToken, newRefreshToken, sessionTtl())) {
      throw new AuthException(AuthErrorCode.INVALID_TOKEN);
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_TOKEN));

    String jti = UUID.randomUUID().toString();
    String newAccessToken = jwtProvider.createAccessToken(user.getId(), jti, user.getRole());
    sessionTokenRepository.save(user.getId(), jti, sessionTtl());

    Instant refreshExpiresAt = Instant.now().plusMillis(jwtProvider.getRefreshTokenExpiration());
    return new AuthTokens(newAccessToken, newRefreshToken, refreshExpiresAt, UserDto.from(user));
  }

  private AuthTokens issueRefreshToken(User user, String accessToken) {
    String refreshToken = jwtProvider.createRefreshToken(user.getId());
    Instant refreshExpiresAt = Instant.now().plusMillis(jwtProvider.getRefreshTokenExpiration());
    refreshTokenRepository.save(user.getId(), refreshToken, sessionTtl());

    return new AuthTokens(accessToken, refreshToken, refreshExpiresAt, UserDto.from(user));
  }

  private Duration sessionTtl() {
    return Duration.ofMillis(jwtProvider.getRefreshTokenExpiration());
  }
}
