package com.codeit.mople.domain.content.client.tmdb.listener;

import com.codeit.mople.domain.content.client.tmdb.batch.TmdbContentItemWriter;
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
import org.springframework.batch.item.ExecutionContext;

@Slf4j
public class TmdbCollectJobListener implements JobExecutionListener {

  private final Counter successCounter;
  private final Counter failureCounter;
  private final Counter skipCounter;
  private final Timer durationTimer;

  public TmdbCollectJobListener(MeterRegistry meterRegistry) {
    this.skipCounter = Counter.builder("batch.tmdb.skip")
        .description("TMDB 수집 배치 skip 건수")
        .register(meterRegistry);
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

  @Override
  public void afterJob(JobExecution jobExecution) {
    Summary summary = summarize(jobExecution);
    Duration duration = elapsed(jobExecution);

    durationTimer.record(duration);
    skipCounter.increment(summary.skipped());

    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
      successCounter.increment();
      log.info("TMDB 수집 배치 완료: {}, 소요={}ms", summary.format(), duration.toMillis());
      return;
    }

    failureCounter.increment();
    log.error("TMDB 수집 배치 실패: status={}, {}, 소요={}ms",
        jobExecution.getStatus(), summary.format(), duration.toMillis(),
        firstFailure(jobExecution));
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

  private Summary summarize(JobExecution jobExecution) {
    long read = 0;
    long delivered = 0;
    long filtered = 0;
    long skipped = 0;
    long inserted = 0;
    long updated = 0;
    long unchanged = 0;

    for (StepExecution step : jobExecution.getStepExecutions()) {
      read += step.getReadCount();
      delivered += step.getWriteCount();
      filtered += step.getFilterCount();
      skipped += step.getSkipCount();

      ExecutionContext context = step.getExecutionContext();
      inserted += context.getLong(TmdbContentItemWriter.INSERTED_KEY, 0L);
      updated += context.getLong(TmdbContentItemWriter.UPDATED_KEY, 0L);
      unchanged += context.getLong(TmdbContentItemWriter.UNCHANGED_KEY, 0L);
    }
    return new Summary(read, delivered, filtered, skipped, inserted, updated, unchanged);
  }

  private record Summary(long read, long delivered, long filtered, long skipped, long inserted, long updated, long unchanged) {
    private String format() {
      return "읽음=%d, 전달=%d, 필터=%d, 신규=%d, 갱신=%d, 무변화=%d, 스킵=%d"
          .formatted(read, delivered, filtered, inserted, updated, unchanged, skipped);
    }
  }


}
