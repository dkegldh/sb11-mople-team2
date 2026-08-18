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
import org.springframework.beans.factory.annotation.Value;
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

  // @Value와 @Scheduled가 같은 프로퍼티(키+기본값)를 참조해야 주기와 TTL이 어긋나지 않는다.
  // 상수 하나로 묶어 두 애노테이션이 공유하게 한다(컴파일타임 상수라 애노테이션에 쓸 수 있음).
  private static final String HEARTBEAT_INTERVAL_PROPERTY =
      "${ws.session.heartbeat-interval-ms:30000}";

  @Value(HEARTBEAT_INTERVAL_PROPERTY)
  private long heartbeatIntervalMs;

  // CONNECT 인증 성공 시 호출 - 로컬 바인딩 + Redis 등록을 함께 수행.
  // Redis 등록은 강제 로그아웃(부가 기능)을 위한 것일 뿐이라, 여기서 실패해도 CONNECT
  // 자체(핵심 기능)는 막지 않는다 - 실패 시 이 세션은 강제 종료 대상에서만 빠질 뿐이다.
  public void registerSession(UUID userId, String sessionId) {
    localRegistry.bindUser(sessionId, userId);
    try {
      String key = userSessionsKey(userId);
      redisTemplate.opsForZSet().add(key, sessionId, nowScore());
      redisTemplate.expire(key, livenessTtl());
      log.debug("WebSocket 세션 등록 - userId: {}, sessionId: {}", userId, sessionId);
    } catch (Exception e) {
      log.warn("WebSocket 세션 Redis 등록 실패(연결은 유지, 강제 로그아웃 대상에서 누락될 수 있음) "
          + "- userId: {}, sessionId: {}", userId, sessionId, e);
    }
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
  // 세션들은 TTL(livenessTtl())이 지나면 자연히 좀비로 걸러진다(별도 정리 배치 불필요).
  @Scheduled(fixedDelayString = HEARTBEAT_INTERVAL_PROPERTY)
  public void refreshHeartbeat() {
    Map<String, UUID> authenticatedSessions = localRegistry.getAuthenticatedSessions();
    if (authenticatedSessions.isEmpty()) {
      return;
    }
    double score = nowScore();
    authenticatedSessions.forEach((sessionId, userId) -> {
      String key = userSessionsKey(userId);
      redisTemplate.opsForZSet().add(key, sessionId, score);
      redisTemplate.expire(key, livenessTtl());
    });
  }

  // 강제 로그아웃 대상 판단용 - TTL 이내에 하트비트가 갱신된(=좀비가 아닌) 세션만 반환.
  // 키 자체의 만료(registerSession/refreshHeartbeat에서 설정)로 무한 누적을 막고 있어,
  // 이 조회는 순수 조회만 수행하고 별도로 Redis 상태를 정리하지 않는다.
  public Set<String> getLiveSessionIds(UUID userId) {
    String key = userSessionsKey(userId);
    double cutoff = cutoffScore();

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
    return Instant.now().minus(livenessTtl()).toEpochMilli();
  }

  // TTL = 하트비트 주기의 3배. 갱신이 한 번 지연돼도(GC 정지 등) 살아있는 세션을 좀비로
  // 오판하지 않도록 여유를 둔다. 프로퍼티 하나에서 주기와 TTL을 함께 유도하므로,
  // 주기만 바뀌고 TTL은 그대로 남는 사고가 구조적으로 불가능하다.
  private Duration livenessTtl() {
    return Duration.ofMillis(heartbeatIntervalMs * 3);
  }

  private String userSessionsKey(UUID userId) {
    return USER_SESSIONS_KEY_PREFIX + userId;
  }
}