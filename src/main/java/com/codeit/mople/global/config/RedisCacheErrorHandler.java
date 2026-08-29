package com.codeit.mople.global.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

// CacheErrorHandler -> Redis가 죽어도 API는 돌아가게 함 (읽기/저장 실패는 넘어가고, 무효화 실패만 재시도로 복구)
@Slf4j
@Component
public class RedisCacheErrorHandler implements CacheErrorHandler {

  // 재시도 횟수
  private static final int MAX_ATTEMPTS = 3;
  // 첫번째 재시도를 몇 초 뒤에 할건지
  private static final long FIRST_DELAY_SECONDS = 1L;
  // 다음 재시도 할 때 대기시간 x 5
  private static final int BACKOFF_MULTIPLIER = 5;
  // 재시도를 기다리는 키의 상한
  private static final int MAX_PENDING = 10_000;
  // 이 시간 안에 다시 실패하면 스택트레이스 없이 debug로만 남김
  private static final long SUPPRESS_WINDOW_MILLIS = Duration.ofSeconds(30).toMillis();
  // 종료 시 진행 중인 재시도를 기다려 줄 시간
  private static final long SHUTDOWN_WAIT_SECONDS = 5L;

  // 비동기로 재시도 수행하기 위한 캐시 무효화 재시도 전용 스레드 풀
  private final ScheduledExecutorService retryScheduler =
      // 싱글스레드 -> 이름"cache-evict-retry" 지정 -> 데몬스레드 지정 -> 스레드 리턴
      Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cache-evict-retry");
        thread.setDaemon(true);
        return thread;
      });

  // 재시도 대기 중인 키. 같은 키에 대한 재시도 체인이 여러 개 생기는 것을 막는다
  private final Set<String> pending = ConcurrentHashMap.newKeySet();
  private final AtomicLong lastReportedAt = new AtomicLong();

  private final Counter getErrorCounter;
  private final Counter putErrorCounter;
  private final Counter evictErrorCounter;
  private final Counter clearErrorCounter;

  public RedisCacheErrorHandler(MeterRegistry meterRegistry) {
    this.getErrorCounter = errorCounter(meterRegistry, "get");
    this.putErrorCounter = errorCounter(meterRegistry, "put");
    this.evictErrorCounter = errorCounter(meterRegistry, "evict");
    this.clearErrorCounter = errorCounter(meterRegistry, "clear");
  }

  private Counter errorCounter(MeterRegistry meterRegistry, String operation) {
    return Counter.builder("cache.error")
        .description("캐시 조작 실패 횟수")
        .tag("operation", operation)
        .register(meterRegistry);
  }

  // 캐시에서 데이터를 조회할 때 에러 발생시 호출
  @Override
  public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
    getErrorCounter.increment();
    report("캐시 조회에 실패해 원본을 조회합니다", exception, cache.getName(), key);
  }

  // 저장 실패는 다음 조회가 미스로 원본을 보게 되므로 값이 틀리지 않음. 재시도x
  @Override
  public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
    putErrorCounter.increment();
    report("캐시 저장에 실패했습니다", exception, cache.getName(), key);
  }

  // 캐시 무효화 실패 -> 재시도
  @Override
  public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
    evictErrorCounter.increment();
    report("캐시 무효화에 실패했습니다", exception, cache.getName(), key);

    String pendingKey = cache.getName() + "::" + key;

    // 같은 키가 이미 대기 중이면 그 재시도가 함께 처리
    if (!pending.add(pendingKey)) {
      return;
    }

    // 대기중인 키 개수가 설정한 제한치를 초과했는지 확인
    if (pending.size() > MAX_PENDING) {
      // 제한치를 초과하면 대기열에서 제거
      pending.remove(pendingKey);
      log.error("재시도 대기열이 가득 차 재시도를 포기합니다. TTL 만료까지 옛 값이 남습니다: cache={}, key={}",
          cache.getName(), key);
      return;
    }

    // 재시도
    retryEvict(cache, key, pendingKey, 1, FIRST_DELAY_SECONDS);
  }

  // 캐시 전체를 비울 때 에러 발생시 호출
  @Override
  public void handleCacheClearError(RuntimeException exception, Cache cache) {
    clearErrorCounter.increment();

    if (shouldReportFully()) {
      log.warn("캐시 전체 삭제에 실패했습니다: cache={}", cache.getName(), exception);
      return;
    }
    log.debug("캐시 전체 삭제에 실패했습니다: cache={}, cause={}", cache.getName(), exception.getMessage());
  }

  @PreDestroy
  void shutdown() {
    retryScheduler.shutdown();
    try {
      if (!retryScheduler.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
        retryScheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      retryScheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }

    Set<String> remaining = Set.copyOf(pending);
    if (!remaining.isEmpty()) {
      log.error("종료로 캐시 무효화 재시도가 취소됐습니다. TTL 만료까지 옛 값이 남습니다: count={}, keys={}",
          remaining.size(), remaining);
    }
  }

  private void report(String message, RuntimeException exception, String cacheName, Object key) {
    if (shouldReportFully()) {
      log.warn("{}: cache={}, key={}", message, cacheName, key, exception);
      return;
    }
    log.debug("{}: cache={}, key={}, cause={}", message, cacheName, key, exception.getMessage());
  }

  private boolean shouldReportFully() {
    long now = System.currentTimeMillis();
    long last = lastReportedAt.get();
    return now - last >= SUPPRESS_WINDOW_MILLIS && lastReportedAt.compareAndSet(last, now);
  }

  // 캐시 무효화 재시도 로직
  private void retryEvict(Cache cache, Object key, String pendingKey, int attempt, long delaySeconds) {
    try {
      retryScheduler.schedule(() -> {
        try {
          cache.evict(key);
          pending.remove(pendingKey);
          log.info("캐시 무효화 재시도 성공: cache={}, key={}, attempt={}", cache.getName(), key, attempt);
        } catch (RuntimeException e) {
          if (attempt < MAX_ATTEMPTS) {
            retryEvict(cache, key, pendingKey, attempt + 1, delaySeconds * BACKOFF_MULTIPLIER);
          } else {
            pending.remove(pendingKey);
            log.error("캐시 무효화 재시도를 모두 실패했습니다. TTL 만료까지 값이 남습니다: cache={}, key={}, cause={}",
                cache.getName(), key, e.getMessage());
          }
        }
      }, delaySeconds, TimeUnit.SECONDS);
    } catch (RejectedExecutionException e) {
      pending.remove(pendingKey);
      log.warn("종료 중이라 캐시 무효화 재시도를 등록하지 못했습니다. TTL 만료까지 옛 값이 남습니다: cache={}, key={}, attempt={}",
          cache.getName(), key, attempt);
    }
  }
}