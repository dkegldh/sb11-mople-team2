package com.codeit.mople.domain.content.client.sportsdb.batch;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SportsBatchScheduler {

  private final JobLauncher jobLauncher;
  private final Job sportsContentJob;
  private final LockProvider lockProvider;

  //서버가 처음 구동 완료되었을 때 자동으로 1회 배치 실행
  @EventListener(ApplicationReadyEvent.class)
  public void runOnStartup() {
    //수동으로 락을 실행해 주는 Executor 생성
    LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);

    //락 설정(이름, 최대 유지 10분, 최소 유지 10초)
    LockConfiguration config = new LockConfiguration(
        Instant.now(),
        "sportsdb-collect",
        Duration.ofMinutes(10),
        Duration.ofSeconds(10)
    );

    // 락을 획득한 서버만 내부 로직(Runnable) 실행
    executor.executeWithLock((Runnable) () -> {
      log.info("서버 구동 시: 다중 서버 락을 획득하여 SportsDB 수집 배치를 시작합니다");
      triggerManualBatch();
    }, config);
  }

  //매일 새벽 4시, 오후 4시에 자동으로 배치 실행
  @Scheduled(cron = "0 0 4,16 * * *")
  @SchedulerLock(name = "sportsdb-collect", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
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
