package com.codeit.mople.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mople.domain.auth.dto.request.ResetPasswordRequest;
import com.codeit.mople.domain.auth.dto.request.SignInRequest;
import com.codeit.mople.domain.auth.dto.response.AuthTokens;
import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  @InjectMocks
  private AuthService authService;

  private User user;

  @BeforeEach
  void setUp() {
    user = User.createUser("test@test.com", "encodedPassword", "testUser");
  }

  @Test
  @DisplayName("로그인 성공 시 토큰을 발급")
  void signIn_success() {
    SignInRequest request = new SignInRequest("test@test.com", "rawPassword");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyLong())).thenReturn("issued-token");
    when(jwtProvider.createRefreshToken(any())).thenReturn("issued-refresh-token");

    AuthTokens response = authService.signIn(request);

    assertThat(response.accessToken()).isEqualTo("issued-token");
  }

  @Test
  @DisplayName("로그인 성공 시 sessionVersion 1 증가")
  void signIn_success_increasesSessionVersion() {
    SignInRequest request = new SignInRequest("test@test.com", "rawPassword");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyLong())).thenReturn("issued-token");
    when(jwtProvider.createRefreshToken(any())).thenReturn("issued-refresh-token");

    authService.signIn(request);

    assertThat(user.getSessionVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("재로그인 시 sessionVersion이 증가하여 이전 토큰의 값과 달라짐")
  void signIn_twice_changesSessionVersionEachTime() {
    SignInRequest request = new SignInRequest("test@test.com", "rawPassword");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyLong())).thenReturn("token");
    when(jwtProvider.createRefreshToken(any())).thenReturn("issued-refresh-token");

    authService.signIn(request);
    long firstSessionVersion = user.getSessionVersion();

    authService.signIn(request);
    long secondSessionVersion = user.getSessionVersion();

    assertThat(secondSessionVersion).isNotEqualTo(firstSessionVersion);
    assertThat(secondSessionVersion).isEqualTo(firstSessionVersion + 1);
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

    authService.resetPassword(request);

    assertThat(user.hasValidTemporaryPassword(Instant.now())).isTrue();
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 비밀번호 초기화를 요청해도 예외 없이 조용히 종료됨 (이메일 존재 여부 노출 방지)")
  void resetPassword_doesNothing_whenEmailNotFound() {
    ResetPasswordRequest request = new ResetPasswordRequest("nobody@test.com");
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

    assertThatCode(() -> authService.resetPassword(request)).doesNotThrowAnyException();

    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  @DisplayName("임시 비밀번호로 로그인이 성공함")
  void signIn_success_withTemporaryPassword() {
    user.issueTemporaryPassword("encodedTempPw", Instant.now().plus(3, ChronoUnit.MINUTES));
    SignInRequest request = new SignInRequest("test@test.com", "temporary1!!");
    when(userRepository.findByEmail(request.username())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("temporary1!!", user.getPassword())).thenReturn(false);
    when(passwordEncoder.matches("temporary1!!", "encodedTempPw")).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyLong())).thenReturn("token");
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
  @DisplayName("로그아웃 시 저장된 Refresh Token이 삭제되고 sessionVersion이 증가하여 기존 토큰이 모두 무효화됨")
  void signOut_success() {
    UUID userId = UUID.randomUUID();
    user.updateRefreshToken("stored-refresh-token", Instant.now().plus(7, ChronoUnit.DAYS));
    long beforeSessionVersion = user.getSessionVersion();
    when(jwtProvider.getUserId("stored-refresh-token")).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    authService.signOut("stored-refresh-token");

    assertThat(user.isRefreshTokenValid("stored-refresh-token", Instant.now())).isFalse();
    assertThat(user.getSessionVersion()).isEqualTo(beforeSessionVersion + 1);
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
    user.updateRefreshToken("valid-refresh-token", Instant.now().plus(7,ChronoUnit.DAYS));
    when(jwtProvider.getUserId("valid-refresh-token")).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(jwtProvider.createAccessToken(any(), anyLong())).thenReturn("new-access-token");
    when(jwtProvider.createRefreshToken(any())).thenReturn("new-refresh-token");
    when(jwtProvider.getRefreshTokenExpiration()).thenReturn(604800000L);

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
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh("some-token"))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("저장된 값과 다른 refreshToken이면 예외가 발생함")
  void refresh_throwsException_whenRefreshTokenMismatch() {
    UUID userId = UUID.randomUUID();
    user.updateRefreshToken("stored-token", Instant.now().plus(7, ChronoUnit.DAYS));
    when(jwtProvider.getUserId("wrong-token")).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> authService.refresh("wrong-token"))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("만료된 refreshToken이면 예외가 발생함")
  void refresh_throwsException_whenRefreshTokenExpired() {
    UUID userId = UUID.randomUUID();
    user.updateRefreshToken("expired-token", Instant.now().minus(1, ChronoUnit.MINUTES));
    when(jwtProvider.getUserId("expired-token")).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> authService.refresh("expired-token"))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("refresh 성공 시 Refresh Token Rotation이 적용되어 저장된 값이 새 값으로 갱신됨")
  void refresh_success_rotateRefreshToken() {
    UUID userId = UUID.randomUUID();
    user.updateRefreshToken("old-token", Instant.now().plus(7, ChronoUnit.DAYS));
    when(jwtProvider.getUserId("old-token")).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(jwtProvider.createAccessToken(any(), anyLong())).thenReturn("new-access");
    when(jwtProvider.createRefreshToken(any())).thenReturn("new-refresh");
    when(jwtProvider.getRefreshTokenExpiration()).thenReturn(604800000L);

    authService.refresh("old-token");

    assertThat(user.isRefreshTokenValid("new-refresh", Instant.now())).isTrue();
    assertThat(user.isRefreshTokenValid("old-token", Instant.now())).isFalse();
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
