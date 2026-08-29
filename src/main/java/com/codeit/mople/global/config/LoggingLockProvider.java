package com.codeit.mople.global.config;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;

// ExtensibleLockProvider -> shedLock에서 락 획득 및 시간 연장 기능 담당 인터페이스
@Slf4j
@RequiredArgsConstructor
public class LoggingLockProvider implements ExtensibleLockProvider {

  private final ExtensibleLockProvider delegate;

  // LockConfiguration(락 설정)을 받음
  @Override
  public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
    // RedisLockProvider에게 redis 락을 걸어달라고 요청
    Optional<SimpleLock> lock = delegate.lock(lockConfiguration);

    // 락 요청 실패 하면 로그 출력
    if (lock.isEmpty()) {
      log.debug("락을 잡지 못해 건너뜁니다: name={}", lockConfiguration.getName());
    }
    return lock;
  }
}