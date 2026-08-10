package com.codeit.mople.domain.content.client.tmdb.batch;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

  public TmdbCollectScheduler(JobLauncher jobLauncher,
      @Qualifier("tmdbCollectJob") Job tmdbCollectJob,
      @Value("${batch.tmdb.collect.max-pages}") int maxPages) {
    this.jobLauncher = jobLauncher;
    this.tmdbCollectJob = tmdbCollectJob;
    this.maxPages = maxPages;
  }

  @Scheduled(cron = "${batch.tmdb.collect.cron}", zone = "Asia/Seoul")
  public void collect() {
    try {
      jobLauncher.run(tmdbCollectJob, jobParameters());
    } catch (JobInstanceAlreadyCompleteException e) {
      log.info("TMDB 수집이 이미 완료되어 건너뜁니다.");
    } catch (JobExecutionException e) {
      log.error("TMDB 수집 Job 실행에 실패했습니다.", e);
    }
  }

  private JobParameters jobParameters() {
    return new JobParametersBuilder()
        .addString(COLLECT_DATE, LocalDate.now(ZONE). toString())
        .addLong(MAX_PAGES, (long) maxPages, false)
        .toJobParameters();
  }
}
