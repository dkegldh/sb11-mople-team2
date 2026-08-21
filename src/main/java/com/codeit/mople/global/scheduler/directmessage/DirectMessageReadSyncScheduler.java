package com.codeit.mople.global.scheduler.directmessage;

import com.codeit.mople.domain.directmessage.repository.DirectMessageReadRedisRepository;
import com.codeit.mople.domain.directmessage.service.DirectMessageReadSyncService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectMessageReadSyncScheduler {

  private final DirectMessageReadRedisRepository readRedisRepository;
  private final DirectMessageReadSyncService readSyncService;

  @Scheduled(fixedDelayString = "${dm.read-sync.delay-ms}")
  @SchedulerLock(
      name = "DirectMessageReadSyncLock",
      lockAtLeastFor = "5s", // 최소 5초간 락 유지
      lockAtMostFor = "30s" // 최대 30초간 락 유지
  )
  public void syncLastReadAtToDb() {
    Set<Object> dirtyMembers = readRedisRepository.getDirtyMembers();

    if (dirtyMembers == null || dirtyMembers.isEmpty()) {
      return;
    }

    log.info("Redis -> DB 읽음 워터마크 동기화 시작 (총 {}건)", dirtyMembers.size());
    int successCount = 0;

    for (Object memberObj : dirtyMembers) {
      String dirtyMember = (String) memberObj;

      Object cachedValue = null;

      try {
        String[] parts = dirtyMember.split(":");
        UUID conversationId = UUID.fromString(parts[0]);
        UUID userId = UUID.fromString(parts[1]);

        cachedValue = readRedisRepository.getLastReadAtForScheduler(dirtyMember);

        if (cachedValue != null) {
          Instant lastReadAt = Instant.parse(cachedValue.toString());

          readSyncService.syncToDb(conversationId, userId, lastReadAt);
          successCount++;
          log.debug("읽음 워터마크 동기화 성공 - conversationId: {}, userId: {}", conversationId, userId);
        } else {
          log.warn("읽음 워터마크 동기화 건너뜀 (존재하지 않거나 삭제된 대화방) - conversationId: {}, userId: {}",
              conversationId, userId);
        }
        // DB 커밋이 무사히 통과했을 때만 레디스 대기열 삭제
        readRedisRepository.removeDirtyMember(dirtyMember);
      } catch (DateTimeParseException e) {
        log.error("Redis 읽음 시각 파싱 실패 (Dirty Set에서 삭제 처리) - member: {}, cachedValue: {}", dirtyMember, cachedValue, e);
        readRedisRepository.removeDirtyMember(dirtyMember);
      } catch (Exception e) {
        log.error("읽음 워터마크 DB 동기화 중 에러 발생 (Dirty Set 유지) - member: {}", dirtyMember, e);
      }
    }

    log.info("Redis -> DB 읽음 워터마크 동기화 완료 (성공: {}/{}건)", successCount, dirtyMembers.size());
  }
}
