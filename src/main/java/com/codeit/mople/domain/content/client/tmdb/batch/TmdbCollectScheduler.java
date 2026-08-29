package com.codeit.mople.domain.content.client.tmdb.batch;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("!load")
@Component
@ConditionalOnProperty(name = "batch.tmdb.collect.enabled", havingValue = "true")
public class TmdbCollectScheduler {

  public static final String LOCK_NAME = "tmdb-collect";

  private final TmdbCollectJobRunner tmdbCollectJobRunner;
  private final String cron;

  public TmdbCollectScheduler(
      TmdbCollectJobRunner tmdbCollectJobRunner,
      @Value("${batch.tmdb.collect.cron}") String cron) {
    this.tmdbCollectJobRunner = tmdbCollectJobRunner;
    this.cron = cron;
  }

  // 앱이 실행되면 log에 현재 cron 설정 표시
  @EventListener(ApplicationReadyEvent.class)
  public void logScheduleState() {
    log.info("TMDB 수집 스케줄러 활성: cron={}", cron);
  }

  // 1. Asia Seoul시간 기준 cron 설정 값에 맞춰서 메서드를 자동 실행해라
  // 2. 분산락 적용(이름, 락 최대 시간 제한, 최소 락 유지시간)
  @Scheduled(cron = "${batch.tmdb.collect.cron}", zone = "Asia/Seoul")
  @SchedulerLock(
      name = LOCK_NAME,
      lockAtMostFor = "${batch.tmdb.collect.lock-at-most-for}",
      lockAtLeastFor = "${batch.tmdb.collect.lock-at-least-for}")
  public void collect() {
    tmdbCollectJobRunner.run();
  }
}
