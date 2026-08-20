package com.codeit.mople.domain.auth.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.codeit.mople.domain.auth.repository.AccountLockRepository;
import com.codeit.mople.domain.auth.repository.RefreshTokenRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.event.ForceLogoutReason;
import com.codeit.mople.global.event.UserAccountStatusChangedEvent;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AccountLockRedisSyncListenerTest {

  @InjectMocks
  private AccountLockRedisSyncListener listener;

  @Mock
  private AccountLockRepository accountLockRepository;

  @Mock
  private SessionTokenRepository sessionTokenRepository;

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Mock
  private UserRepository userRepository;

  private final UUID userId = UUID.randomUUID();

  @Test
  @DisplayName("ACCOUNT_LOCKED 이벤트일 경우 세션/리프레시 토큰을 무효화하고 Redis 잠금을 설정함")
  void handle_syncsAllRedisState_whenAccountLocked() {
    // when
    listener.handle(new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ACCOUNT_LOCKED, true));

    // then
    verify(sessionTokenRepository).invalidate(userId);
    verify(refreshTokenRepository).invalidate(userId);
    verify(accountLockRepository).lock(userId);
  }

  @Test
  @DisplayName("ACCOUNT_UNLOCKED 이벤트일 경우 Redis 잠금만 해제하고 세션/리프레시는 건드리지 않음")
  void handle_onlyUnlocksRedis_whenAccountUnlocked() {
    // when
    listener.handle(new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ACCOUNT_UNLOCKED, false));

    // then
    verify(accountLockRepository).unlock(userId);
    verify(sessionTokenRepository, never()).invalidate(any());
    verify(refreshTokenRepository, never()).invalidate(any());
  }

  @Test
  @DisplayName("ROLE_CHANGE 등 잠금과 무관한 이벤트는 무시함")
  void handle_ignoresUnrelatedReason() {
    // when
    listener.handle(new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ROLE_CHANGE, true));

    // then
    verifyNoMoreInteractions(accountLockRepository, sessionTokenRepository, refreshTokenRepository);
  }

  @Test
  @DisplayName("첫 시도가 실패하면 재시도해서 결국 성공함")
  void handle_retriesAndSucceeds_whenFirstAttemptFails() {
    // given
    willThrow(new RuntimeException("redis down"))
        .willDoNothing()
        .given(accountLockRepository).lock(userId);
    User lockedUser = User.createUser("test@test.com", "encoded", "tester");
    lockedUser.lock();
    given(userRepository.findById(userId)).willReturn(Optional.of(lockedUser));

    // when
    listener.handle(new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ACCOUNT_LOCKED, true));

    // then
    verify(accountLockRepository, timeout(2000).times(2)).lock(userId);
  }

  @Test
  @DisplayName("첫 시도에서는 DB 상태를 다시 확인하지 않고 바로 적용함")
  void handle_appliesImmediately_onFirstAttempt_withoutCheckingDb() {
    // when
    listener.handle(new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ACCOUNT_LOCKED, true));

    // then
    verify(accountLockRepository).lock(userId);
    verifyNoInteractions(userRepository);
  }

  @Test
  @DisplayName("재시도 시점에 DB 상태가 이미 바뀌었으면 재적용을 건너뜀")
  void handle_skipsRetry_whenDbStateAlreadyChanged() {
    // given - 첫 시도는 실패, 그 사이 관리자가 잠금을 해제했다고 가정
    willThrow(new RuntimeException("redis down")).given(accountLockRepository).lock(userId);
    User unlockedUser = User.createUser("test@test.com", "encoded", "테스터");
    given(userRepository.findById(userId)).willReturn(Optional.of(unlockedUser));

    // when
    listener.handle(new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ACCOUNT_LOCKED, true));

    // then - 재시도 시점에 DB가 이미 unlocked라 lock()이 다시 호출되지 않음(최초 1번뿐)
    verify(userRepository, timeout(2000)).findById(userId);
    verify(accountLockRepository, timeout(2000).times(1)).lock(userId);
  }

  @Test
  @DisplayName("재시도 시점에도 DB 상태가 같으면 정상적으로 재적용함")
  void handle_retriesNormally_whenDbStateStillMatches() {
    // given - 첫 시도만 실패, DB는 여전히 잠금 상태
    willThrow(new RuntimeException("redis down"))
        .willDoNothing()
        .given(accountLockRepository).lock(userId);
    User lockedUser = User.createUser("test@test.com", "encoded", "테스터");
    lockedUser.lock();
    given(userRepository.findById(userId)).willReturn(Optional.of(lockedUser));

    // when
    listener.handle(new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ACCOUNT_LOCKED, true));

    // then - DB 상태가 같으므로 재시도가 정상적으로 두 번째 호출까지 진행됨
    verify(accountLockRepository, timeout(2000).times(2)).lock(userId);
  }
}
