package com.codeit.mople.domain.content.client.tmdb.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;

import com.codeit.mople.global.config.RedisNamespaceProperties;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "batch.tmdb.collect.enabled=true",
    "batch.tmdb.collect.lock-at-most-for=PT2S",
    "batch.tmdb.collect.lock-at-least-for=PT0S"
})
@DisplayName("TMDB 수집 스케줄러 락 만료 통합 테스트")
class TmdbCollectSchedulerLockExpiryTest {

  private static final Duration EXPIRY_WAIT = Duration.ofSeconds(10);

  @Autowired
  private TmdbCollectScheduler scheduler;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private RedisNamespaceProperties redisNamespaceProperties;

  @MockitoBean
  private TmdbCollectJobRunner runner;

  private String lockKey;

  @BeforeEach
  void setUp() {
    lockKey = redisNamespaceProperties.lockKey(TmdbCollectScheduler.LOCK_NAME);
    stringRedisTemplate.delete(lockKey);
  }

  @AfterEach
  void tearDown() {
    stringRedisTemplate.delete(lockKey);
  }

  @Test
  @DisplayName("락이 먼저 만료돼 두 번째 인스턴스가 락을 새로 잡은 뒤에 첫 번째가 늦게 끝나도 남의 락을 지우지 않는지")
  void collect_AfterLockExpired_KeepsLockHeldByOther() throws Exception {
    // given
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch firstEntered = new CountDownLatch(1);
    CountDownLatch secondEntered = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch releaseSecond = new CountDownLatch(1);

    willAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        firstEntered.countDown();
        releaseFirst.await(10, TimeUnit.SECONDS);
      } else {
        secondEntered.countDown();
        releaseSecond.await(10, TimeUnit.SECONDS);
      }
      return null;
    }).given(runner).run();

    ExecutorService pool = Executors.newFixedThreadPool(2);

    try {
      // given 첫 번째 인스턴스가 락을 잡고 작업에 들어감
      Future<?> first = pool.submit(scheduler::collect);
      assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();

      // given 작업이 끝나기 전에 락이 lockAtMostFor로 먼저 사라짐
      awaitLockExpired();

      // given 두 번째 인스턴스가 같은 이름으로 락을 새로 잡고 작업에 들어감
      Future<?> second = pool.submit(scheduler::collect);
      assertThat(secondEntered.await(5, TimeUnit.SECONDS)).isTrue();

      String lockHeldBySecond = lockValue();
      assertThat(lockHeldBySecond).isNotNull();

      // when 뒤늦게 첫 번째 인스턴스가 작업을 마치고 락을 해제
      releaseFirst.countDown();
      first.get(5, TimeUnit.SECONDS);

      // then 두 번째 인스턴스가 쥔 락이 그대로 남아 있어야 함
      assertThat(lockValue()).isEqualTo(lockHeldBySecond);

      releaseSecond.countDown();
      second.get(5, TimeUnit.SECONDS);
    } finally {
      releaseFirst.countDown();
      releaseSecond.countDown();
      pool.shutdownNow();
    }
  }

  private void awaitLockExpired() throws InterruptedException {
    long deadline = System.nanoTime() + EXPIRY_WAIT.toNanos();
    while (System.nanoTime() < deadline) {
      if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("lockAtMostFor가 지났는데도 락 키가 남아 있습니다: key=" + lockKey);
  }

  private String lockValue() {
    return stringRedisTemplate.opsForValue().get(lockKey);
  }
}