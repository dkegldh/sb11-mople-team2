package com.codeit.mople.domain.content.client.tmdb.batch;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TmdbCollectScheduler {

  private static final String COLLECT_DATE = "collectDate";
  private static final String MAX_PAGES = "maxPages";
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  private final JobLauncher jobLauncher;
  private final Job tmdbCollectJob;
  private final int maxPages;
  // 서버 여러대를 띄우면 수집이 중복되므로 한 대에서만 true로 둠
  private final boolean enabled;
  private final String cron;

  public TmdbCollectScheduler(JobLauncher jobLauncher,
      @Qualifier("tmdbCollectJob") Job tmdbCollectJob,
      @Value("${batch.tmdb.collect.max-pages}") int maxPages,
      @Value("${batch.tmdb.collect.enabled}") boolean enabled,
      @Value("${batch.tmdb.collect.cron}") String cron) {
    this.jobLauncher = jobLauncher;
    this.tmdbCollectJob = tmdbCollectJob;
    this.maxPages = maxPages;
    this.enabled = enabled;
    this.cron = cron;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void logScheduleState() {
    log.info("TMDB 수집 스케줄러: enabled={}, cron={}, maxPages={}", enabled, cron, maxPages);
  }

  @Scheduled(cron = "${batch.tmdb.collect.cron}", zone = "Asia/Seoul")
  public void collect() {
    if (!enabled) {
      log.info("TMDB 수집 스케줄러가 꺼져 있어 건너뜁니다.");
      return;
    }

    try {
      JobExecution execution = jobLauncher.run(tmdbCollectJob, jobParameters());
      if (execution.getStatus() != BatchStatus.COMPLETED) {
        log.error("TMDB 수집 Job이 비정상 종료: status={}, exitCode={}",
            execution.getStatus(), execution.getExitStatus().getExitCode());
      }
    } catch (JobInstanceAlreadyCompleteException e) {
      log.info("TMDB 수집이 이미 완료되어 건너뜁니다.");
    } catch (JobExecutionException e) {
      log.error("TMDB 수집 Job 실행에 실패했습니다.", e);
    }
  }

  // 배치가 collectDate와 maxPages(true)를 식별자로 둠
  private JobParameters jobParameters() {
    return new JobParametersBuilder()
        .addString(COLLECT_DATE, LocalDate.now(ZONE).toString())
        .addLong(MAX_PAGES, (long) maxPages, true)
        .toJobParameters();
  }
}
