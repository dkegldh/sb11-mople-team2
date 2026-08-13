package com.codeit.mople.domain.user.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.error.CustomException;
import com.codeit.mople.global.event.ForceLogoutReason;
import com.codeit.mople.global.event.UserAccountStatusChangedEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

  @InjectMocks
  private AdminService adminService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Captor
  private ArgumentCaptor<UserAccountStatusChangedEvent> eventCaptor;

  UUID userId;   // 대상 유저
  UUID adminId;  // 현재 로그인한 어드민 (요청자)
  User user;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    adminId = UUID.randomUUID();
    user = User.createUser("test@test.com", "encoded", "테스터");

    CustomUserDetails principal = new CustomUserDetails(adminId, Role.ADMIN);
    Authentication auth = new UsernamePasswordAuthenticationToken(
        principal, null, principal.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("사용자 권한 변경")
  class ChangeUserRole {

    @Test
    @DisplayName("USER을 ADMIN으로 권한 변경 할 수 있고 강제 로그아웃 이벤트를 발행한다")
    void USER을_ADMIN으로_권한_변경_할_수_있고_강제_로그아웃_이벤트를_발행한다() {
      // given
      user.updateRefreshToken("old-refresh-token", Instant.now().plusSeconds(3600));
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when
      adminService.changeUserRole(userId, "ADMIN");

      // then
      assertThat(user.getRole()).isEqualTo(Role.ADMIN);
      // sessionVersion만 올리고 refresh token을 안 지우면, 유저가 /refresh로 최신
      // sessionVersion이 박힌 새 access token을 받아 강제 로그아웃을 우회할 수 있다.
      assertThat(user.getRefreshToken()).isNull();
      verify(eventPublisher).publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
      assertThat(eventCaptor.getValue().sessionInvalidated()).isTrue();
    }

    @Test
    @DisplayName("ADMIN을 USER로 강등할 수 있고 강제 로그아웃 이벤트를 발행한다")
    void ADMIN을_USER로_강등할_수_있고_강제_로그아웃_이벤트를_발행한다() {
      // given
      User admin = User.createAdmin("admin@test.com", "encoded", "어드민");
      admin.updateRefreshToken("old-refresh-token", Instant.now().plusSeconds(3600));
      given(userRepository.findById(userId)).willReturn(Optional.of(admin));

      // when
      adminService.changeUserRole(userId, "USER");

      // then
      assertThat(admin.getRole()).isEqualTo(Role.USER);
      assertThat(admin.getRefreshToken()).isNull();
      verify(eventPublisher).publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
      assertThat(eventCaptor.getValue().sessionInvalidated()).isTrue();
    }

    @Test
    @DisplayName("같은 권한으로 변경하면 강제 로그아웃 이벤트를 발행하지 않는다")
    void 같은_권한으로_변경하면_강제_로그아웃_이벤트를_발행하지_않는다() {
      // given - user는 기본 USER 역할
      user.updateRefreshToken("old-refresh-token", Instant.now().plusSeconds(3600));
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when
      adminService.changeUserRole(userId, "USER");

      // then
      assertThat(user.getRole()).isEqualTo(Role.USER);
      // 실제로 권한이 바뀐 게 없으므로 refresh token도 그대로 유지돼야 한다.
      assertThat(user.getRefreshToken()).isEqualTo("old-refresh-token");
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자 id로 요청하면 USER_NOT_FOUND 예외가 발생한다")
    void 존재하지_않는_사용자_id로_요청하면_USER_NOT_FOUND_예외가_발생한다() {
      // given
      given(userRepository.findById(userId)).willReturn(Optional.empty());

      // when & then
      assertThatExceptionOfType(CustomException.class)
          .isThrownBy(() -> adminService.changeUserRole(userId, "ADMIN"))
          .extracting(CustomException::getErrorCode)
          .isEqualTo(UserErrorCode.USER_NOT_FOUND);

      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("유효하지 않은 role 값이면 IllegalArgumentException이 발생한다")
    void 유효하지_않은_role_값이면_IllegalArgumentException이_발생한다() {
      // Role.valueOf()가 DB 조회 전에 먼저 실행되므로 별도 스텁 불필요
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> adminService.changeUserRole(userId, "INVALID_ROLE"));

      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("자신의 권한을 변경하려 하면 CANNOT_MODIFY_SELF 예외가 발생한다")
    void 자신의_권한을_변경하려_하면_CANNOT_MODIFY_SELF_예외가_발생한다() {
      assertThatExceptionOfType(CustomException.class)
          .isThrownBy(() -> adminService.changeUserRole(adminId, "USER"))
          .extracting(CustomException::getErrorCode)
          .isEqualTo(UserErrorCode.CANNOT_MODIFY_SELF);

      verify(eventPublisher, never()).publishEvent(any());
    }
  }

  @Nested
  @DisplayName("계정 잠금 상태 변경")
  class ChangeUserLocked {

    @Test
    @DisplayName("locked(true)이면 계정을 잠금하고 sessionVersion을 올리며 강제 로그아웃 이벤트를 발행한다")
    void locked_true이면_계정을_잠금하고_sessionVersion을_올리며_강제_로그아웃_이벤트를_발행한다() {
      // given
      user.updateRefreshToken("old-refresh-token", Instant.now().plusSeconds(3600));
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when
      adminService.changeUserLocked(userId, true);

      // then
      assertThat(user.isLocked()).isTrue();
      assertThat(user.getSessionVersion()).isEqualTo(1);
      assertThat(user.getRefreshToken()).isNull();
      verify(eventPublisher).publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
      assertThat(eventCaptor.getValue().reason()).isEqualTo(ForceLogoutReason.ACCOUNT_LOCKED);
      assertThat(eventCaptor.getValue().sessionInvalidated()).isTrue();
    }

    @Test
    @DisplayName("locked(false)이면 계정 잠금을 해제하고 알림 이벤트를 발행하지만 sessionVersion은 변경하지 않는다")
    void locked_false이면_계정_잠금을_해제하고_알림_이벤트를_발행하지만_sessionVersion은_변경하지_않는다() {
      // given
      user.lock();
      user.updateRefreshToken("old-refresh-token", Instant.now().plusSeconds(3600));
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when
      adminService.changeUserLocked(userId, false);

      // then
      assertThat(user.isLocked()).isFalse();
      assertThat(user.getSessionVersion()).isEqualTo(0);
      // 세션을 안 끊었으니 refresh token도 그대로 살아있어야 한다.
      assertThat(user.getRefreshToken()).isEqualTo("old-refresh-token");
      verify(eventPublisher).publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
      assertThat(eventCaptor.getValue().reason()).isEqualTo(ForceLogoutReason.ACCOUNT_UNLOCKED);
      assertThat(eventCaptor.getValue().sessionInvalidated()).isFalse();
    }

    @Test
    @DisplayName("이미 잠금 상태인 계정에 잠금 요청 시 이벤트를 발행하지 않고 sessionVersion도 변경하지 않는다")
    void 이미_잠금_상태인_계정에_잠금_요청_시_이벤트를_발행하지_않고_sessionVersion도_변경하지_않는다() {
      // given
      user.lock();
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when
      adminService.changeUserLocked(userId, true);

      // then
      assertThat(user.isLocked()).isTrue();
      assertThat(user.getSessionVersion()).isEqualTo(0);
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("이미 잠금 해제 상태인 계정에 잠금 해제 요청 시 이벤트를 발행하지 않는다")
    void 이미_잠금_해제_상태인_계정에_잠금_해제_요청_시_이벤트를_발행하지_않는다() {
      // given - user는 기본적으로 잠금 해제(unlocked) 상태
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when
      adminService.changeUserLocked(userId, false);

      // then
      assertThat(user.isLocked()).isFalse();
      assertThat(user.getSessionVersion()).isEqualTo(0);
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자 id로 요청하면 USER_NOT_FOUND 예외가 발생한다")
    void 존재하지_않는_사용자_id로_요청하면_USER_NOT_FOUND_예외가_발생한다() {
      // given
      given(userRepository.findById(userId)).willReturn(Optional.empty());

      // when & then
      assertThatExceptionOfType(CustomException.class)
          .isThrownBy(() -> adminService.changeUserLocked(userId, true))
          .extracting(CustomException::getErrorCode)
          .isEqualTo(UserErrorCode.USER_NOT_FOUND);

      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("자신의 계정을 잠금하려 하면 CANNOT_MODIFY_SELF 예외가 발생한다")
    void 자신의_계정을_잠금하려_하면_CANNOT_MODIFY_SELF_예외가_발생한다() {
      assertThatExceptionOfType(CustomException.class)
          .isThrownBy(() -> adminService.changeUserLocked(adminId, true))
          .extracting(CustomException::getErrorCode)
          .isEqualTo(UserErrorCode.CANNOT_MODIFY_SELF);

      verify(eventPublisher, never()).publishEvent(any());
    }
  }
}
