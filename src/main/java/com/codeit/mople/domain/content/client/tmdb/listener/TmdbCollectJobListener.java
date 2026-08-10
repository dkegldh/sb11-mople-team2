package com.codeit.mople.domain.content.client.tmdb.listener;

import com.codeit.mople.domain.content.client.tmdb.TmdbGenreCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;

@Slf4j
public class TmdbCollectJobListener implements JobExecutionListener {

  private final TmdbGenreCache genreCache;
  private final Counter successCounter;
  private final Counter failureCounter;
  private final Timer durationTimer;

  public TmdbCollectJobListener(TmdbGenreCache genreCache, MeterRegistry meterRegistry) {
    this.genreCache = genreCache;
    this.successCounter = Counter.builder("batch.tmdb.success")
        .description("TMDB 수집 배치 성공 횟수")
        .register(meterRegistry);
    this.failureCounter = Counter.builder("batch.tmdb.failure")
        .description("TMDB 수집 배치 실패 횟수")
        .register(meterRegistry);
    this.durationTimer = Timer.builder("batch.tmdb.duration")
        .description("TMDB 수집 배치 소요 시간")
        .register(meterRegistry);
  }

  // 장르 태그가 비어있지 않도록 수집 시작 전에 캐시 갱신
  @Override
  public void beforeJob(JobExecution jobExecution) {
    genreCache.refresh();
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    long readCount = 0;
    long writeCount = 0;
    long filterCount = 0;
    for (StepExecution step : jobExecution.getStepExecutions()) {
      readCount += step.getReadCount();
      writeCount += step.getWriteCount();
      filterCount += step.getFilterCount();
    }

    Duration duration = elapsed(jobExecution);
    durationTimer.record(duration);

    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
      successCounter.increment();
      log.info("TMDB 수집 배치 완료: 읽음={}, 저장={}, 필터={}, 소요={}ms",
          readCount, writeCount, filterCount, duration.toMillis());
      return;
    }

    failureCounter.increment();
    log.error("TMDB 수집 배치 실패: status={}, 읽음={}, 저장={}, 필터={}, 소요={}ms",
        jobExecution.getStatus(), readCount, writeCount, filterCount, duration.toMillis(), firstFailure(jobExecution));
  }

  private Duration elapsed(JobExecution jobExecution) {
    LocalDateTime startTime = jobExecution.getStartTime();
    LocalDateTime endTime = jobExecution.getEndTime();

    if (startTime == null || endTime == null) {
      return Duration.ZERO;
    }
    return Duration.between(startTime, endTime);
  }

  private Throwable firstFailure(JobExecution jobExecution) {
    List<Throwable> failures = jobExecution.getAllFailureExceptions();
    return failures.isEmpty() ? null : failures.get(0);
  }


}
