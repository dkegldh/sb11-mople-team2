package com.codeit.mople.domain.content.client.sportsdb.batch;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SportsBatchScheduler {

  private final JobLauncher jobLauncher;
  private final Job sportsContentJob;

  //매일 새벽 4시에 자동으로 배치 실행
  @Scheduled(cron = "0 0 4 * * *")
  public void runBatchJobAutomatically() {
    log.info("자동 스케줄러: SportsDB 수집 배치를 시작합니다");
    triggerManualBatch();
  }

  //필요 시(관리자 호출, 테스트 등) 외부에서 즉시 호출할 수 있는 수동 실행 트리거 메서드
  public void triggerManualBatch() {
    try {
      JobParameters jobParameters = new JobParametersBuilder()
          .addString("time", LocalDateTime.now().toString()) //매번 다른 파라미터를 주어 새로운 Job 인스턴스로 실행
          .toJobParameters();

      jobLauncher.run(sportsContentJob, jobParameters);
      log.info("SportsDB 배치 실행이 성공적으로 요청되었습니다");
    } catch (Exception e) {
      log.error("SportsDB 배치 실행 중 오류 발생", e);
    }
  }
}
