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
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TmdbCollectJobRunner {

  private static final String COLLECT_DATE = "collectDate";
  private static final String MAX_PAGES = "maxPages";
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  // 배치 Job 실행기
  private final JobLauncher jobLauncher;
  // 실행 할 배치 작업
  private final Job tmdbCollectJob;
  // 실행 범위
  private final int maxPages;

  public TmdbCollectJobRunner(
      JobLauncher jobLauncher,
      @Qualifier("tmdbCollectJob") Job tmdbCollectJob,
      @Value("${batch.tmdb.collect.max-pages}") int maxPages) {
    this.jobLauncher = jobLauncher;
    this.tmdbCollectJob = tmdbCollectJob;
    this.maxPages = maxPages;
  }

  public void run() {
    log.info("TMDB 수집을 시작합니다: maxPages={}", maxPages);
    try {
      JobExecution execution = jobLauncher.run(tmdbCollectJob, jobParameters());
      if (execution.getStatus() != BatchStatus.COMPLETED) {
        log.error("TMDB 수집 Job이 비정상 종료: status={}, exitCode={}",
            execution.getStatus(), execution.getExitStatus().getExitCode());
      }
    } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException e) {
      log.info("TMDB 수집이 이미 완료되었거나 실행 중이라 건너뜁니다.");
    } catch (DataIntegrityViolationException e) {
      log.info("이미 다른 프로세스에서 TMDB 수집 작업을 시작하여 건너뜁니다.");
    } catch (JobExecutionException e) {
      log.error("TMDB 수집 Job 실행에 실패했습니다.", e);
    }
  }

  // 배치가 collectDate와 maxPages를 식별자로 둠
  private JobParameters jobParameters() {
    return new JobParametersBuilder()
        .addString(COLLECT_DATE, LocalDate.now(ZONE).toString())
        .addLong(MAX_PAGES, (long) maxPages, true)
        .toJobParameters();
  }
}