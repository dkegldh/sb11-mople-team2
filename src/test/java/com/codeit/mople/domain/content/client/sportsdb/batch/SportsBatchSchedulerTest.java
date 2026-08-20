package com.codeit.mople.domain.content.client.sportsdb.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

@ExtendWith(MockitoExtension.class)
class SportsBatchSchedulerTest {

  @Mock
  private JobLauncher jobLauncher;

  @Mock
  private Job sportsContentJob;

  @Mock
  private LockProvider lockProvider;

  @InjectMocks
  private SportsBatchScheduler scheduler;

  @Test
  @DisplayName("수동 트리거(triggerManualBatch) 호출 시 JobLauncher가 실행된다")
  void triggerManualBatch_Success() throws Exception {
    scheduler.triggerManualBatch();

    verify(jobLauncher, times(1)).run(eq(sportsContentJob), any(JobParameters.class));
  }

  @Test
  @DisplayName("서버 구동 완료 이벤트(runOnStartup) 발생 시 락을 획득하고 배치가 실행된다")
  void runOnStartup_Success() throws Exception {
    //락 획득에 성공했다고 가정
    SimpleLock dummyLock = mock(SimpleLock.class);
    when(lockProvider.lock(any())).thenReturn(Optional.of(dummyLock));

    scheduler.runOnStartup();

    verify(jobLauncher, times(1)).run(eq(sportsContentJob), any(JobParameters.class));
  }

  @Test
  @DisplayName("자동 스케줄러(runBatchJobAutomatically) 동작 시 배치가 실행된다")
  void runBatchJobAutomatically_Success() throws Exception {
    scheduler.runBatchJobAutomatically();

    verify(jobLauncher, times(1)).run(eq(sportsContentJob), any(JobParameters.class));
  }

  @Test
  @DisplayName("자동 스케줄러 메서드에 분산 락(SchedulerLock) 애노테이션이 올바르게 설정되어 있다")
  void runBatchJobAutomatically_HasSchedulerLockAnnotation() throws NoSuchMethodException {
    Method method = SportsBatchScheduler.class.getMethod("runBatchJobAutomatically");

    SchedulerLock schedulerLock = method.getAnnotation(SchedulerLock.class);

    assertThat(schedulerLock).isNotNull(); //어노테이션이 존재하는지 검증
    assertThat(schedulerLock.name()).isEqualTo("sportsdb-collect");
    assertThat(schedulerLock.lockAtMostFor()).isEqualTo("PT10M");
    assertThat(schedulerLock.lockAtLeastFor()).isEqualTo("PT10S");
  }

  @Test
  @DisplayName("서버 구동 시 수동 락 설정(이름, 유지 시간)이 올바르게 구성되어 실행된다")
  void runOnStartup_LockConfiguration() {
    SimpleLock dummyLock = mock(SimpleLock.class);
    when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(dummyLock));

    scheduler.runOnStartup();

    ArgumentCaptor<LockConfiguration> captor = ArgumentCaptor.forClass(LockConfiguration.class);
    verify(lockProvider).lock(captor.capture()); //lockProvider.lock() 호출 시 전달된 파라미터 캡처

    LockConfiguration config = captor.getValue();
    assertThat(config.getName()).isEqualTo("sportsdb-collect");
    assertThat(config.getLockAtMostFor()).isEqualTo(Duration.ofMinutes(10));
    assertThat(config.getLockAtLeastFor()).isEqualTo(Duration.ofSeconds(10));
  }
}