package com.codeit.mople.domain.directmessage.repository;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DirectMessageReadRedisRepository {

  private final RedisTemplate<String, Object> redisTemplate;

  private static final String READ_KEY_PREFIX = "dm:read:";
  // DB와 값이 달라져서 동기화가 필요한 대상들을 모아두는 Set
  private static final String DIRTY_SET_KEY = "dm:read:dirty";
  // 7일 동안 조회/수정이 없는 유저의 읽음 키는 레디스에서 자동 삭제
  private static final Duration READ_DATA_TTL = Duration.ofDays(7);

  private static final DateTimeFormatter FIXED_WIDTH_FORMATTER = DateTimeFormatter
      .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'")
      .withZone(ZoneId.of("UTC"));

  private static final DefaultRedisScript<Long> SAVE_LAST_READ_SCRIPT;

  static {
    // 레디스에 있는 기존 시간을 확인(GET) -> 기존 시간보다 미래면 덮어쓰기(SET) -> 대기열 추가(SADD)
    SAVE_LAST_READ_SCRIPT = new DefaultRedisScript<>();
    SAVE_LAST_READ_SCRIPT.setScriptText(
        "local cur = redis.call('GET', KEYS[1]) " +
            "if cur and cur >= ARGV[1] then return 0 end " +
            "redis.call('SET', KEYS[1], ARGV[1], 'EX', " + READ_DATA_TTL.toSeconds() + ") " +
            "redis.call('SADD', KEYS[2], ARGV[2]) " +
            "return 1"
    );
    SAVE_LAST_READ_SCRIPT.setResultType(Long.class);
  }

  // 유저가 DM 메시지를 읽었을 때 레디스에 최신 시각을 기록하고 Dirty Set에 등록
  public boolean saveLastReadAt(UUID conversationId, UUID userId, Instant readAt) {
    try {
      String valueKey = READ_KEY_PREFIX + conversationId + ":" + userId;
      String dirtyMember = conversationId + ":" + userId;

      String readAtStr = FIXED_WIDTH_FORMATTER.format(readAt);

      Long result = redisTemplate.execute(
          SAVE_LAST_READ_SCRIPT,
          List.of(valueKey, DIRTY_SET_KEY), // KEYS[1], KEYS[2]
          readAtStr, dirtyMember // ARGV[1], ARGV[2]
      );

      if (result != null && result == 0L) {
        log.debug("Redis 읽음 워터마크 역행 방지: 기존 최신 시각이 존재하여 갱신 무시 - key: {}, requestTime: {}", valueKey, readAtStr);
      } else {
        log.info("Redis 읽음 시각 기록 및 대기열 추가 완료(Lua) - key: {}, readAt: {}", valueKey, readAtStr);
      }

      return true;
    } catch (Exception e) {
      log.error("Redis 장애 감지: DB에 직접 읽음 시각 업데이트 (Fallback) 시도 - key: {}", conversationId, e);
      return false;
    }
  }

  // [조회 전용] 오직 레디스 캐시만 확인하여 반환
  public Optional<Instant> getCachedLastReadAt(UUID conversationId, UUID userId) {
    String valueKey = READ_KEY_PREFIX + conversationId + ":" + userId;

    try {
      Object cachedValue = redisTemplate.opsForValue().get(valueKey);

      // 레디스에 최신 값이 있으면 바로 반환
      if (cachedValue != null) {
        log.info("Redis Cache Hit, Redis에서 데이터 로드 - key: {}", valueKey);
        return Optional.of(Instant.parse(cachedValue.toString()));
      }
    } catch (DateTimeParseException e) {
      log.error("Redis 읽음 시각 파싱 실패, 해당 키 삭제 - key: {}", valueKey, e);
      redisTemplate.delete(valueKey);
    } catch (Exception e) {
      log.error("Redis 읽음 시각 조회 장애 감지 - key: {}", valueKey, e);
    }

    log.debug("Redis Cache Miss - key: {}", valueKey);
    return Optional.empty();
  }

  // [저장 전용] 레디스에 값 캐싱 복구
  public void setCachedLastReadAt(UUID conversationId, UUID userId, Instant lastReadAt) {
    String valueKey = READ_KEY_PREFIX + conversationId + ":" + userId;
    try {
      // 레디스에 값 캐싱 복구 및 setIfAbsent를 사용하여 동시성 에러 방어
      redisTemplate.opsForValue().setIfAbsent(valueKey, FIXED_WIDTH_FORMATTER.format(lastReadAt), READ_DATA_TTL);
    } catch (Exception e) {
      log.error("Redis 복구 중 예외 발생 (무시하고 진행) - key: {}", valueKey, e);
    }
  }

  // [스케줄러 전용] 스케줄러 대신 키를 조립하는 헬퍼 메서드
  public String getLastReadAtForScheduler(String dirtyMember) {
    String valueKey = READ_KEY_PREFIX + dirtyMember;
    Object value = redisTemplate.opsForValue().get(valueKey);
    return value != null ? value.toString() : null;
  }

  public Set<Object> getDirtyMembers() {
    return redisTemplate.opsForSet().members(DIRTY_SET_KEY);
  }

  public void removeDirtyMember(String dirtyMember) {
    log.info("Redis Dirty Set 항목 삭제 - member: {}", dirtyMember);
    redisTemplate.opsForSet().remove(DIRTY_SET_KEY, dirtyMember);
  }
}
