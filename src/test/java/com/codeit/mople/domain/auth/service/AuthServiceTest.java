package com.codeit.mople.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.codeit.mople.domain.auth.dto.request.SignInRequest;
import com.codeit.mople.domain.auth.dto.response.TokenResponse;
import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import java.util.Optional;
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
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyLong())).thenReturn("issued-token");

    TokenResponse response = authService.signIn(request);

    assertThat(response.accessToken()).isEqualTo("issued-token");
  }

  @Test
  @DisplayName("로그인 성공 시 sessionVersion 1 증가")
  void signIn_success_increasesSessionVersion() {
    SignInRequest request = new SignInRequest("test@test.com", "rawPassword");
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyLong())).thenReturn("issued-token");

    authService.signIn(request);

    assertThat(user.getSessionVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("재로그인 시 sessionVersion이 증가하여 이전 토큰의 값과 달라짐")
  void signIn_twice_changesSessionVersionEachTime() {
    SignInRequest request = new SignInRequest("test@test.com", "rawPassword");
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtProvider.createAccessToken(any(), anyLong())).thenReturn("token");

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
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("비밀번호가 일치하지 않으면 예외가 발생")
  void signIn_throwsException_whenPasswordMismatch() {
    SignInRequest request = new SignInRequest("test@test.com", "wrongPassword");
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
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
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(lockedUser));
    when(passwordEncoder.matches(request.password(), lockedUser.getPassword())).thenReturn(true);

    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(AuthException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOCKED_ACCOUNT);
  }

  @Test
  @DisplayName("로그인 실패 시 details가 비어있다 (보안상 계정 존재 여부 노출 방지)")
  void signIn_throwsException_withoutDetails() {
    SignInRequest request = new SignInRequest("nobody@test.com", "rawPassword");
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.signIn(request))
        .isInstanceOf(AuthException.class)
        .satisfies(e -> {
          AuthException ae = (AuthException) e;
          assertThat(ae.getDetails()).isEmpty();
        });
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
