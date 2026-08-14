package com.codeit.mople.realtime.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// SimpMessagingTemplate은 일부러 의존하지 않는다: JwtChannelInterceptor가 CONNECT 시점에
// registerSession()을 호출하기 위해 이 서비스를 주입받는데, SimpMessagingTemplate 빈은
// WebSocketMessageBrokerConfigurer(=WebSocketConfig, jwtChannelInterceptor를 필요로 함)가
// 전부 초기화돼야 만들어져서 순환 참조가 생긴다. 그래서 실제 종료/알림(SimpMessagingTemplate
// 필요)은 이 서비스가 아니라 WebSocketForceDisconnectListener가 전담한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketSessionRegistryService {

  private final RedisTemplate<String, Object> redisTemplate;
  private final LocalWebSocketSessionRegistry localRegistry;

  private static final String USER_SESSIONS_KEY_PREFIX = "ws:user-sessions:";
  public static final String FORCE_DISCONNECT_CHANNEL = "ws:force-disconnect";

  // 하트비트 갱신 주기(30초)보다 여유 있게 잡아, 갱신 지연/GC 정지 등으로 인한
  // 오탐(살아있는데 죽은 걸로 판단)을 막는다. 이 기간 이상 갱신이 없으면 좀비로 간주.
  private static final Duration SESSION_LIVENESS_TTL = Duration.ofSeconds(90);

  // CONNECT 인증 성공 시 호출 - 로컬 바인딩 + Redis 등록을 함께 수행
  public void registerSession(UUID userId, String sessionId) {
    localRegistry.bindUser(sessionId, userId);
    redisTemplate.opsForZSet().add(userSessionsKey(userId), sessionId, nowScore());
    log.debug("WebSocket 세션 등록 - userId: {}, sessionId: {}", userId, sessionId);
  }

  // 연결 종료(정상/비정상/강제종료 공통) 시 호출 - 로컬에 바인딩돼 있었던 경우에만 Redis에서도 제거
  public void removeSession(String sessionId) {
    localRegistry.removeConnection(sessionId).ifPresent(userId -> {
      redisTemplate.opsForZSet().remove(userSessionsKey(userId), sessionId);
      log.debug("WebSocket 세션 제거 - userId: {}, sessionId: {}", userId, sessionId);
    });
  }

  // 이 인스턴스가 로컬에 들고 있는 인증된 세션들의 Redis 생존 기록을 주기적으로 갱신한다.
  // 인스턴스가 비정상 종료되면 이 스케줄러도 같이 멈추므로, 해당 인스턴스가 들고 있던
  // 세션들은 SESSION_LIVENESS_TTL이 지나면 자연히 좀비로 걸러진다(별도 정리 배치 불필요).
  @Scheduled(fixedDelay = 30_000)
  public void refreshHeartbeat() {
    Map<String, UUID> authenticatedSessions = localRegistry.getAuthenticatedSessions();
    if (authenticatedSessions.isEmpty()) {
      return;
    }
    double score = nowScore();
    authenticatedSessions.forEach((sessionId, userId) ->
        redisTemplate.opsForZSet().add(userSessionsKey(userId), sessionId, score));
  }

  // 강제 로그아웃 대상 판단용 - TTL 이내에 하트비트가 갱신된(=좀비가 아닌) 세션만 반환
  public Set<String> getLiveSessionIds(UUID userId) {
    String key = userSessionsKey(userId);
    double cutoff = cutoffScore();

    // 조회 시점에 만료된 좀비 기록을 함께 걷어내 Redis에 죽은 항목이 무한정 쌓이는 것을 막는다.
    redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoff - 1);

    Set<Object> members = redisTemplate.opsForZSet().rangeByScore(key, cutoff, Double.MAX_VALUE);
    if (members == null || members.isEmpty()) {
      return Collections.emptySet();
    }
    return members.stream().map(Object::toString).collect(Collectors.toSet());
  }

  // Redis Pub/Sub으로 모든 인스턴스에 강제 종료 신호를 방송한다.
  // 실제 종료는 각 인스턴스가 자신의 로컬 레지스트리를 확인해 스스로 판단한다.
  public void publishForceDisconnect(UUID userId, String reason) {
    redisTemplate.convertAndSend(FORCE_DISCONNECT_CHANNEL, new ForceDisconnectMessage(userId, reason));
  }

  private double nowScore() {
    return Instant.now().toEpochMilli();
  }

  private double cutoffScore() {
    return Instant.now().minus(SESSION_LIVENESS_TTL).toEpochMilli();
  }

  private String userSessionsKey(UUID userId) {
    return USER_SESSIONS_KEY_PREFIX + userId;
  }
}