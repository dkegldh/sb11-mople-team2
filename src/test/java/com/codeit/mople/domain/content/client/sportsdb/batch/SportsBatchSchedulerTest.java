package com.codeit.mople.domain.content.client.sportsdb.batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  @InjectMocks
  private SportsBatchScheduler scheduler;

  @Test
  @DisplayName("수동 트리거(triggerManualBatch) 호출 시 JobLauncher가 실행된다")
  void triggerManualBatch_Success() throws Exception {
    scheduler.triggerManualBatch();

    verify(jobLauncher, times(1)).run(eq(sportsContentJob), any(JobParameters.class));
  }

  @Test
  @DisplayName("서버 구동 완료 이벤트(runOnStartup) 발생 시 배치가 실행된다")
  void runOnStartup_Success() throws Exception {
    scheduler.runOnStartup();

    verify(jobLauncher, times(1)).run(eq(sportsContentJob), any(JobParameters.class));
  }

  @Test
  @DisplayName("자동 스케줄러(runBatchJobAutomatically) 동작 시 배치가 실행된다")
  void runBatchJobAutomatically_Success() throws Exception {
    scheduler.runBatchJobAutomatically();

    verify(jobLauncher, times(1)).run(eq(sportsContentJob), any(JobParameters.class));
  }
}