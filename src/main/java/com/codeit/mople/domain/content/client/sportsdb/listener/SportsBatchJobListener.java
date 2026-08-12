package com.codeit.mople.domain.content.client.sportsdb.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SportsBatchJobListener implements JobExecutionListener {

  private final Counter successCounter;
  private final Counter failureCounter;

  // 생성자에서 Micrometer 메트릭을 직접 등록
  public SportsBatchJobListener(MeterRegistry meterRegistry) {
    this.successCounter = Counter.builder("batch.sports.success")
        .description("SportsDB 수집 배치 성공 횟수")
        .register(meterRegistry);
    this.failureCounter = Counter.builder("batch.sports.failure")
        .description("SportsDB 수집 배치 실패 횟수")
        .register(meterRegistry);
  }

  //Job 실행 전 시작 시간 및 로깅 기록
  @Override
  public void beforeJob(JobExecution jobExecution) {
    log.info("SportsDB Batch Job 시작 - 시간: {}", jobExecution.getStartTime());
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
      //Job 종료 후 성공 시 Actuator 성공 메트릭 카운트 증가
      log.info("SportsDB Batch Job 성공적으로 완료");
      successCounter.increment();
    } else {
      //Job 종료 후 실패 시 Actuator 실패 메트릭 카운트 증가
      log.error("SportsDB Batch Job 실패 - 최종 상태: {}", jobExecution.getStatus());
      failureCounter.increment();
    }
  }
}
