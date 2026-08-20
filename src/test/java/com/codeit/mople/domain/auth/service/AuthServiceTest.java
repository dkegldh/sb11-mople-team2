package com.codeit.mople.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mople.domain.auth.dto.request.ResetPasswordRequest;
import com.codeit.mople.domain.auth.dto.request.SignInRequest;
import com.codeit.mople.domain.auth.dto.response.AuthTokens;
import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.auth.repository.PasswordResetRateLimiterRepository;
import com.codeit.mople.domain.auth.repository.RefreshTokenRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.user.entity.AuthProvider;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtProvider jwtProvider;

  @Mock
  private SessionTokenRepository sessionTokenRepository;

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Mock
  private AuthMailService authMailService;

  @Mock
  private PasswordResetRateLimiterRepository passwordResetRateLimiterRepository;

  @InjectMocks
  private AuthService authService;

  private User user;

  private static final long REFRESH_TOKEN_EXPIRATION_MS = 604800000L;
  private static final Duration EXPECTED_TTL = Duration.ofMillis(REFRESH_TOKEN_EXPIRATION_MS);

  @BeforeEach
  void setUp() {
    user = User.createUser("test@test.com", "encodedPassword", "testUser");
    lenient().when(jwtProvider.getRefreshTokenExpiration()).thenReturn(REFRESH_TOKEN_EXPIRATION_MS);
    lenient().when(passwordResetRateLimiterRepository.tryAcquireGlobal(anyInt(), any())).thenReturn(true);
    lenient().when(passwordResetRateLimiterRepository.tryAcquireByIp(anyString(), anyInt(), any())).thenReturn(true);
    lenient().when(passwordResetRateLimiterRepository.tryAcquireByEmail(anyString(), any())).thenReturn(true);
  }

  @Test
  @DisplayName("로그인 성공 시 토큰을 발급")
  void signIn_success() {
    SignInRequest request = new SignInRequest("test@test.com", "rawPassword");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyString(), any(Role.class))).thenReturn("issued-token");
    when(jwtProvider.createRefreshToken(any())).thenReturn("issued-refresh-token");

    AuthTokens response = authService.signIn(request);

    assertThat(response.accessToken()).isEqualTo("issued-token");
  }

  @Test
  @DisplayName("가입 시와 다른 대소문자로 입력해도 로그인이 성공함")
  void signIn_success_withDifferentEmailCase() {
    SignInRequest request = new SignInRequest("TEST@TEST.COM", "rawPassword");
    when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyString(), any(Role.class))).thenReturn("issued-token");
    when(jwtProvider.createRefreshToken(any())).thenReturn("issued-refresh-token");

    AuthTokens response = authService.signIn(request);

    assertThat(response.accessToken()).isEqualTo("issued-token");
  }

  @Test
  @DisplayName("로그인 성공 시 세션 토큰이 Redis에 저장됨")
  void signIn_success_savesSessionToken() {
    SignInRequest request = new SignInRequest("test@test.com", "rawPassword");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyString(), any(Role.class))).thenReturn("issued-token");
    when(jwtProvider.createRefreshToken(any())).thenReturn("issued-refresh-token");

    authService.signIn(request);

    verify(sessionTokenRepository).save(eq(user.getId()), anyString(), eq(EXPECTED_TTL));
  }

  @Test
  @DisplayName("재로그인 시 매번 새로운 세션 토큰(jti)이 발급되어 이전 토큰의 값과 달라짐")
  void signIn_twice_generatesDifferentSessionTokenEachTime() {
    SignInRequest request = new SignInRequest("test@test.com", "rawPassword");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyString(), any(Role.class))).thenReturn("token");
    when(jwtProvider.createRefreshToken(any())).thenReturn("issued-refresh-token");

    authService.signIn(request);
    authService.signIn(request);

    ArgumentCaptor<String> jtiCaptor = ArgumentCaptor.forClass(String.class);
    verify(sessionTokenRepository, times(2)).save(eq(user.getId()), jtiCaptor.capture(), eq(EXPECTED_TTL));
    assertThat(jtiCaptor.getAllValues().get(0)).isNotEqualTo(jtiCaptor.getAllValues().get(1));
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 로그인 시 예외가 발생")
  void signIn_throwsException_whenEmailNotFound() {
    SignInRequest request = new SignInRequest("nobody@test.com", "rawPassword");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("비밀번호가 일치하지 않으면 예외가 발생")
  void signIn_throwsException_whenPasswordMismatch() {
    SignInRequest request = new SignInRequest("test@test.com", "wrongPassword");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(false);

    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 로그인을 시도해도 이메일 존재 여부를 알 수 없는 동일한 예외를 반환")
  void signIn_returnsSameErrorCode_forNonExistentEmailAndWrongPassword() {
    // 이메일이 존재하지 않는 경우
    when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());
    AuthErrorCode errorCodeForMissingEmail = catchAuthErrorCode(new SignInRequest("nobody@test.com", "any"));

    // 이메일은 존재하지만 비밀번호가 틀린 경우
    when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrongPw", user.getPassword())).thenReturn(false);
    AuthErrorCode errorCodeForWrongPassword = catchAuthErrorCode(new SignInRequest("test@test.com", "wrongPw"));

    assertThat(errorCodeForMissingEmail).isEqualTo(errorCodeForWrongPassword);
  }

  @Test
  @DisplayName("잠긴 계정으로 로그인 시 예외가 발생")
  void signIn_throwsException_whenAccountIsLocked() {
    User lockedUser = User.createUser("locked@test.com", "encodedPw", "lockedUser");
    lockedUser.lock();

    SignInRequest request = new SignInRequest("locked@test.com", "rawPw123");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(lockedUser));
    when(passwordEncoder.matches(request.password(), lockedUser.getPassword())).thenReturn(true);

    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOCKED_ACCOUNT);
  }

  @Test
  @DisplayName("로그인 실패 시 details가 비어있다 (보안상 계정 존재 여부 노출 방지)")
  void signIn_throwsException_withoutDetails() {
    SignInRequest request = new SignInRequest("nobody@test.com", "rawPassword");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(AuthException.class)
        .satisfies(e -> {
          AuthException ae = (AuthException) e;
          assertThat(ae.getDetails()).isEmpty();
        });
  }

  @Test
  @DisplayName("존재하는 이메일로 비밀번호 초기화 요청 시 임시 비밀번호가 발급됨")
  void resetPassword_success() {
    ResetPasswordRequest request = new ResetPasswordRequest("test@test.com");
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
    when(passwordEncoder.encode(any())).thenReturn("encodedTempPw");

    authService.resetPassword(request, "127.0.0.1");

    assertThat(user.hasValidTemporaryPassword(Instant.now())).isTrue();
    verify(authMailService).sendTemporaryPassword(eq("test@test.com"), anyString());
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 비밀번호 초기화를 요청해도 예외 없이 조용히 종료됨 (이메일 존재 여부 노출 방지)")
  void resetPassword_doesNothing_whenEmailNotFound() {
    ResetPasswordRequest request = new ResetPasswordRequest("nobody@test.com");
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

    assertThatCode(() -> authService.resetPassword(request, "127.0.0.1")).doesNotThrowAnyException();

    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  @DisplayName("동일 이메일로 유효시간 내 재요청 시 예외가 발생하고 메일이 발송되지 않음")
  void resetPassword_throwsException_whenRateLimited() {
    ResetPasswordRequest request = new ResetPasswordRequest("test@test.com");
    when(passwordResetRateLimiterRepository.tryAcquireByEmail(eq("test@test.com"), any())).thenReturn(false);

    assertThatThrownBy(() -> authService.resetPassword(request, "127.0.0.1"))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TOO_MANY_RESET_PASSWORD_REQUEST);

    verify(userRepository, never()).findByEmail(any());
    verify(authMailService, never()).sendTemporaryPassword(any(), any());
  }

  @Test
  @DisplayName("같은 IP에서 한도를 초과해 요청하면 예외가 발생하고 메일이 전송되지 않음")
  void resetPassword_throwsException_whenIpRateLimited() {
    ResetPasswordRequest request = new ResetPasswordRequest("test@test.com");
    when(passwordResetRateLimiterRepository.tryAcquireByIp(eq("127.0.0.1"), anyInt(), any())).thenReturn(false);

    assertThatThrownBy(() -> authService.resetPassword(request, "127.0.0.1"))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TOO_MANY_RESET_PASSWORD_REQUEST);

    verify(userRepository, never()).findByEmail(any());
    verify(authMailService, never()).sendTemporaryPassword(any(), any());
  }

  @Test
  @DisplayName("전체 발송 한도를 초과하면 예외가 발생하고 메일이 발송되지 않음")
  void resetPassword_throwsException_whenGlobalRateLimited() {
    ResetPasswordRequest request = new ResetPasswordRequest("test@test.com");
    when(passwordResetRateLimiterRepository.tryAcquireGlobal(anyInt(), any())).thenReturn(false);

    assertThatThrownBy(() -> authService.resetPassword(request, "127.0.0.1"))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TOO_MANY_RESET_PASSWORD_REQUEST);

    verify(userRepository, never()).findByEmail(any());
    verify(authMailService, never()).sendTemporaryPassword(any(), any());
  }

  @Test
  @DisplayName("임시 비밀번호로 로그인이 성공함")
  void signIn_success_withTemporaryPassword() {
    user.issueTemporaryPassword("encodedTempPw", Instant.now().plus(3, ChronoUnit.MINUTES));
    SignInRequest request = new SignInRequest("test@test.com", "temporary1!!");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("temporary1!!", user.getPassword())).thenReturn(false);
    when(passwordEncoder.matches("temporary1!!", "encodedTempPw")).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyString(), any(Role.class))).thenReturn("token");
    when(jwtProvider.createRefreshToken(any())).thenReturn("refreshToken");

    AuthTokens response = authService.signIn(request);

    assertThat(response.accessToken()).isEqualTo("token");
  }

  @Test
  @DisplayName("만료된 임시 비밀번호로는 로그인이 실패함")
  void signIn_throwsException_whenTemporaryPasswordExpired() {
    user.issueTemporaryPassword("encodedTemPw", Instant.now().minus(1, ChronoUnit.MINUTES));
    SignInRequest request = new SignInRequest("test@test.com", "temporary1!!");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("temporary1!!", user.getPassword())).thenReturn(false);

    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("정식 비밀번호로 변경한 뒤에는 이전 임시 비밀번호로 로그인할 수 없음")
  void signIn_throwsException_whenTemporaryPasswordDestroyedAfterPasswordChange() {
    user.issueTemporaryPassword("encodedTempPw", Instant.now().plus(3, ChronoUnit.MINUTES));
    user.destroyTemporaryPassword(); // UserService.changePassword()가 호출하는 것과 동일한 시나리오

    SignInRequest request = new SignInRequest("test@test.com", "temporary1!!");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("temporary1!!", user.getPassword())).thenReturn(false);

    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("로그아웃 시 저장된 Refresh Token이 삭제되고 기존 토큰이 모두 무효화됨")
  void signOut_success() {
    UUID userId = UUID.randomUUID();
    when(jwtProvider.getUserId("stored-refresh-token")).thenReturn(userId);
    when(refreshTokenRepository.isValid(userId, "stored-refresh-token")).thenReturn(true);

    authService.signOut("stored-refresh-token");

    verify(refreshTokenRepository).invalidate(userId);
    verify(sessionTokenRepository).invalidate(userId);
  }

  @Test
  @DisplayName("refreshToken이 없으면 아무 처리도 하지 않고 조용히 종료됨")
  void signOut_doesNothing_whenRefreshTokenIsNull() {
    assertThatCode(() -> authService.signOut(null)).doesNotThrowAnyException();

    verify(userRepository, never()).findById(any());
  }

  @Test
  @DisplayName("유효하지 않은 refreshToken이면 아무 처리도 하지 않고 조용히 종료됨")
  void signOut_doesNothing_whenRefreshTokenIsInvalid() {
    when(jwtProvider.getUserId("broken-token")).thenThrow(new io.jsonwebtoken.MalformedJwtException("broken"));

    assertThatCode(() -> authService.signOut("broken-token")).doesNotThrowAnyException();

    verify(userRepository, never()).findById(any());
  }

  @Test
  @DisplayName("유효한 refreshToken으로 재발급에 성공")
  void refresh_success() {
    UUID userId = UUID.randomUUID();
    when(jwtProvider.getUserId("valid-refresh-token")).thenReturn(userId);
    when(jwtProvider.createRefreshToken(userId)).thenReturn("new-refresh-token");
    when(refreshTokenRepository.rotate(eq(userId), eq("valid-refresh-token"), eq("new-refresh-token"), eq(EXPECTED_TTL))).thenReturn(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(jwtProvider.createAccessToken(any(), anyString(), any(Role.class))).thenReturn("new-access-token");

    AuthTokens response = authService.refresh("valid-refresh-token");

    assertThat(response.accessToken()).isEqualTo("new-access-token");
    assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
  }

  @Test
  @DisplayName("파싱할 수 없는 refreshToken이면 예외가 발생")
  void refresh_throwsException_whenTokenIsInvalidFormat() {
    when(jwtProvider.getUserId("broken-token")).thenThrow(new io.jsonwebtoken.MalformedJwtException("broken"));

    assertThatThrownBy(() -> authService.refresh("broken-token"))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("토큰의  사용자를 찾을 수 없으면 예외가 발생함")
  void refresh_throwsException_whenUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(jwtProvider.getUserId("some-token")).thenReturn(userId);
    when(jwtProvider.createRefreshToken(userId)).thenReturn("new-refresh-token");
    when(refreshTokenRepository.rotate(eq(userId), eq("some-token"), eq("new-refresh-token"), eq(EXPECTED_TTL))).thenReturn(true);
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh("some-token"))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("저장된 값과 다른 refreshToken이거나 동시 요청으로 이미 교체가 된 경우 예외가 발생함")
  void refresh_throwsException_whenRotateFails() {
    UUID userId = UUID.randomUUID();
    when(jwtProvider.getUserId("wrong-token")).thenReturn(userId);
    when(jwtProvider.createRefreshToken(userId)).thenReturn("new-refresh-token");
    when(refreshTokenRepository.rotate(eq(userId), eq("wrong-token"), eq("new-refresh-token"), eq(EXPECTED_TTL))).thenReturn(false);

    assertThatThrownBy(() -> authService.refresh("wrong-token"))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);

    verify(userRepository, never()).findById(any());
  }

  @Test
  @DisplayName("잠긴 계정이면 refreshToken이 유효해도 재발급이 거부됨")
  void refresh_throwsException_whenAccountIsLocked() {
    UUID userId = UUID.randomUUID();
    User lockedUser = User.createUser("locked@test.com", "encodedPw", "lockedUser");
    lockedUser.lock();
    when(jwtProvider.getUserId("locked-user-token")).thenReturn(userId);
    when(jwtProvider.createRefreshToken(userId)).thenReturn("new-refresh-token");
    when(refreshTokenRepository.rotate(eq(userId), eq("locked-user-token"), eq("new-refresh-token"), eq(EXPECTED_TTL))).thenReturn(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(lockedUser));

    assertThatThrownBy(() -> authService.refresh("locked-user-token"))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOCKED_ACCOUNT);
  }

  @Test
  @DisplayName("refresh 성공 시 Refresh Token Rotation이 원자적으로 적용됨")
  void refresh_success_rotateRefreshToken() {
    UUID userId = UUID.randomUUID();
    when(jwtProvider.getUserId("old-token")).thenReturn(userId);
    when(jwtProvider.createRefreshToken(userId)).thenReturn("new-refresh");
    when(refreshTokenRepository.rotate(eq(userId), eq("old-token"), eq("new-refresh"), eq(EXPECTED_TTL))).thenReturn(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(jwtProvider.createAccessToken(any(), anyString(), any(Role.class))).thenReturn("new-access");

    authService.refresh("old-token");

    verify(refreshTokenRepository).rotate(eq(userId), eq("old-token"), eq("new-refresh"), eq(EXPECTED_TTL));
  }

  @Test
  @DisplayName("소셜 로그인 계정으로 이메일/비밀번호 로그인 시도 시 비밀번호 검증 없이 예외가 발생함")
  void signIn_throwsException_whenAccountIsOAuthUser() {
    User googleUser = User.createOAuthUser("oauth@test.com", "oauthUser", null, AuthProvider.GOOGLE, "google-sub");
    SignInRequest request = new SignInRequest("oauth@test.com", "anyPassword");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(googleUser));

    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);

    verify(passwordEncoder, never()).matches(any(), any());
  }

  private AuthErrorCode catchAuthErrorCode(SignInRequest request) {
    try {
      authService.signIn(request);
      throw new AssertionError("예외가 발생해야 합니다.");
    } catch (AuthException e) {
      return (AuthErrorCode) e.getErrorCode();
    }
  }
}
