package com.codeit.mople.domain.auth.event;

import com.codeit.mople.domain.auth.repository.AccountLockRepository;
import com.codeit.mople.domain.auth.repository.RefreshTokenRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.event.ForceLogoutReason;
import com.codeit.mople.global.event.UserAccountStatusChangedEvent;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountLockRedisSyncListener {

  private static final int MAX_ATTEMPTS = 3;
  private static final long FIRST_DELAY_SECONDS = 1L;
  private static final int BACKOFF_MULTIPLIER = 5;

  private final AccountLockRepository accountLockRepository;
  private final SessionTokenRepository sessionTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;

  private final ScheduledExecutorService retryScheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "account-lock-redis-sync-retry");
        t.setDaemon(true);
        return t;
      });

  @PreDestroy
  void shutdown() {
    retryScheduler.shutdownNow();
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(UserAccountStatusChangedEvent event) {
    if(event.reason() != ForceLogoutReason.ACCOUNT_LOCKED && event.reason() != ForceLogoutReason.ACCOUNT_UNLOCKED) {
      return;
    }
    boolean locked = event.reason() == ForceLogoutReason.ACCOUNT_LOCKED;
    syncRedis(event.userId(), locked, 1, FIRST_DELAY_SECONDS);
  }

  private void syncRedis(UUID userId, boolean locked, int attempt, long delaySeconds) {
    if(attempt > 1 && !isStillCurrent(userId, locked)) {
      log.info("계정 잠금 상태가 재시도 대기 중 변경되어 재적용을 건너뜁니다 - userId: {}, 예정된 작업: locked={}", userId, locked);
      return;
    }
    try {
      if(locked) {
        sessionTokenRepository.invalidate(userId);
        refreshTokenRepository.invalidate(userId);
        accountLockRepository.lock(userId);
      } else {
        accountLockRepository.unlock(userId);
      }
      log.info("계정 잠금 Redis 동기화 성공 - userId: {}, locked: {}, attempt: {}", userId, locked, attempt);
    } catch (RuntimeException e) {
      if(attempt < MAX_ATTEMPTS) {
        retryScheduler.schedule(
            () -> syncRedis(userId, locked, attempt + 1, delaySeconds * BACKOFF_MULTIPLIER),
            delaySeconds, TimeUnit.SECONDS);
        } else {
        log.error("계정 잠금 Redis 동기화를 모두 실패했습니다 - userId: {}, locked: {} (DB와 불일치 상태로 남음)", userId, locked, e);
      }
    }
  }

  private boolean isStillCurrent(UUID userId, boolean locked) {
    return userRepository.findById(userId)
        .map(user -> user.isLocked() == locked)
        .orElse(false);
  }
}
