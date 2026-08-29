package com.codeit.mople.global.scheduler.directmessage;

import com.codeit.mople.domain.directmessage.repository.DirectMessageReadRedisRepository;
import com.codeit.mople.domain.directmessage.service.DirectMessageReadSyncService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
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
    Set<Object> dirtyMembers = readRedisRepository.getDirtyMembersWithLimit();

    if (dirtyMembers == null || dirtyMembers.isEmpty()) {
      return;
    }

    log.info("Redis -> DB 읽음 워터마크 동기화 시작 - 대상: {}건 (최대 500건 제한)", dirtyMembers.size());

    int successCount = 0;

    // 일괄 삭제를 위해 처리가 완료된 멤버를 모아두는 Set
    Set<Object> successfullyProcessed = new HashSet<>();

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

          boolean isSuccess = readSyncService.syncToDb(conversationId, userId, lastReadAt);

          if (isSuccess) {
            successCount++;
            log.debug("읽음 워터마크 동기화 성공 - conversationId: {}, userId: {}", conversationId, userId);
          } else {
            log.warn("읽음 워터마크 동기화 건너뜀: 존재하지 않는 대화방 - conversationId: {}", conversationId);
          }
        } else {
          log.warn("읽음 워터마크 동기화 건너뜀: Redis 키 없음(TTL 만료 추정) - conversationId: {}, userId: {}",
              conversationId, userId);
        }

        successfullyProcessed.add(memberObj);

      } catch (DateTimeParseException | IllegalArgumentException | IndexOutOfBoundsException e) {
        log.error("Redis 대기열 데이터 포맷/파싱 에러 (Dirty Set에서 삭제 처리) - member: {}, cachedValue: {}",
            dirtyMember, cachedValue, e);
        successfullyProcessed.add(memberObj);
      } catch (Exception e) {
        log.error("읽음 워터마크 DB 동기화 중 에러 발생 (Dirty Set 유지) - member: {}", dirtyMember, e);
      }
    }

    if (!successfullyProcessed.isEmpty()) {
      readRedisRepository.removeProcessedMembers(successfullyProcessed);
    }

    log.info("Redis -> DB 읽음 워터마크 동기화 완료 (성공: {}/{}건)", successCount, dirtyMembers.size());
  }
}
