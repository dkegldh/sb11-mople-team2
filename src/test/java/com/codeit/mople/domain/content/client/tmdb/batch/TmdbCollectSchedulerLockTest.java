package com.codeit.mople.domain.content.client.tmdb.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codeit.mople.global.config.RedisNamespaceProperties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "batch.tmdb.collect.enabled=true")
@DisplayName("TMDB 수집 스케줄러 분산 락 통합 테스트")
class TmdbCollectSchedulerLockTest {

  private static final String EXPECTED_LOCK_KEY = "mople:test:lock:tmdb-collect";

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

  @Nested
  @DisplayName("중복 실행 방지")
  class MutualExclusion {

    @Test
    @DisplayName("첫 번째 스레드가 락을 쥐고 있는 도중에 두번째 스레드가 들어오면 잘 튕겨내는지")
    void collect_WhileLockHeld_SkipsWithoutRunning() throws Exception {
      // given
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      willAnswer(invocation -> {
        entered.countDown();
        release.await(5, TimeUnit.SECONDS);
        return null;
      }).given(runner).run();

      ExecutorService pool = Executors.newSingleThreadExecutor();
      Future<?> holder = pool.submit(scheduler::collect);

      try {
        // 첫 호출이 락을 잡고 실행에 들어갈 때까지 기다림
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        // when 락이 잡혀 있는 동안 두 번째 호출
        scheduler.collect();

        // then
        verify(runner, times(1)).run();
      } finally {
        release.countDown();
        holder.get(5, TimeUnit.SECONDS);
        pool.shutdown();
      }
    }

    @Test
    @DisplayName("작업이 끝났다고 락을 바로 해제하지 않고 설정한 최소 시간 동안 다음 실행을 막아주는지")
    void collect_AfterCompletion_StillBlockedByLockAtLeastFor() {
      // given
      scheduler.collect();

      // when
      scheduler.collect();

      // then
      verify(runner, times(1)).run();
    }
  }

  @Nested
  @DisplayName("락 키")
  class LockKey {

    @Test
    @DisplayName("환경 네임스페이스가 의도한 형태로 락 키가 만들어지는지")
    void collect_CreatesLockKeyUnderNamespace() {
      // when
      scheduler.collect();

      // then
      assertThat(lockKey).isEqualTo(EXPECTED_LOCK_KEY);
      assertThat(stringRedisTemplate.hasKey(EXPECTED_LOCK_KEY)).isTrue();
    }
  }
}