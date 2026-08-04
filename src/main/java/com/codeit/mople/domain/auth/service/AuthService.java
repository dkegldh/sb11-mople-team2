package com.codeit.mople.domain.auth.service;

import com.codeit.mople.domain.auth.dto.request.ResetPasswordRequest;
import com.codeit.mople.domain.auth.dto.request.SignInRequest;
import com.codeit.mople.domain.auth.dto.response.AuthTokens;
import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.user.dto.response.UserDto;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import io.jsonwebtoken.JwtException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  @Transactional
  public AuthTokens signIn(SignInRequest request) {
    User user = userRepository.findByEmail(request.username())
        .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));

    if(!isPasswordValid(request.password(), user)) {
      throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    if(user.isLocked()) {
      throw new AuthException(AuthErrorCode.LOCKED_ACCOUNT);
    }

    long newSessionVersion = user.increaseSessionVersion();
    String accessToken = jwtProvider.createAccessToken(user.getId(), newSessionVersion);

    return issueRefreshToken(user, accessToken);
  }

  private boolean isPasswordValid(String rawPassword, User user) {
    if(passwordEncoder.matches(rawPassword, user.getPassword())) {
      return true;
    }
    return user.hasValidTemporaryPassword(Instant.now())
        && passwordEncoder.matches(rawPassword, user.getTemporaryPassword());
  }

  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    userRepository.findByEmail(request.email())
        .ifPresent(user -> {
          String temporaryPassword = generateTemporaryPassword();
          Instant expiresAt = Instant.now().plus(TEMPORARY_PASSWORD_EXPIRATION_MINUTES, ChronoUnit.MINUTES);
          user.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword), expiresAt);
          // TODO: 이메일 발송
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

    userRepository.findById(userId).ifPresent(user -> {
      if(!user.isRefreshTokenValid(refreshToken, Instant.now())) {
        return;
      }
      user.clearRefreshToken();
      user.increaseSessionVersion();
    });
  }

  @Transactional
  public AuthTokens refresh(String refreshToken) {
    UUID userId;
    try {
      userId = jwtProvider.getUserId(refreshToken);
    } catch (JwtException | IllegalArgumentException e) {
      throw new AuthException(AuthErrorCode.INVALID_TOKEN);
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_TOKEN));

    if(!user.isRefreshTokenValid(refreshToken, Instant.now())) {
      throw new AuthException(AuthErrorCode.INVALID_TOKEN);
    }

    String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getSessionVersion());
    return issueRefreshToken(user, newAccessToken);
  }

  private AuthTokens issueRefreshToken(User user, String accessToken) {
    String refreshToken = jwtProvider.createRefreshToken(user.getId());
    Instant refreshExpiresAt = Instant.now().plusMillis(jwtProvider.getRefreshTokenExpiration());
    user.updateRefreshToken(refreshToken, refreshExpiresAt);

    return new AuthTokens(accessToken, refreshToken, refreshExpiresAt, UserDto.from(user));
  }
}
