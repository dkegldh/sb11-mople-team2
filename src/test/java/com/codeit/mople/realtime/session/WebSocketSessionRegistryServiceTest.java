package com.codeit.mople.realtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketSessionRegistryService")
class WebSocketSessionRegistryServiceTest {

  @InjectMocks
  private WebSocketSessionRegistryService registryService;

  @Mock
  private RedisTemplate<String, Object> redisTemplate;

  @Mock
  private LocalWebSocketSessionRegistry localRegistry;

  @Mock
  private ZSetOperations<String, Object> zSetOperations;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
  }

  @Test
  @DisplayName("registerSession은 로컬 바인딩과 Redis ZADD를 함께 수행한다")
  void registerSession_bindsLocalAndAddsToRedis() {
    registryService.registerSession(userId, "session-1");

    verify(localRegistry).bindUser("session-1", userId);
    verify(zSetOperations).add(eq("ws:user-sessions:" + userId), eq("session-1"), anyDouble());
  }

  @Test
  @DisplayName("removeSession은 로컬에 바인딩돼 있었던 경우에만 Redis에서도 제거한다")
  void removeSession_boundSession_removesFromRedis() {
    given(localRegistry.removeConnection("session-1")).willReturn(Optional.of(userId));

    registryService.removeSession("session-1");

    verify(zSetOperations).remove("ws:user-sessions:" + userId, "session-1");
  }

  @Test
  @DisplayName("removeSession은 로컬에 바인딩된 적 없는 세션이면 Redis를 건드리지 않는다")
  void removeSession_unboundSession_doesNotTouchRedis() {
    given(localRegistry.removeConnection("session-1")).willReturn(Optional.empty());

    registryService.removeSession("session-1");

    verify(zSetOperations, never()).remove(anyString(), any());
  }

  @Test
  @DisplayName("refreshHeartbeat은 로컬에 인증된 세션 각각에 대해 Redis 점수를 갱신한다")
  void refreshHeartbeat_refreshesEachAuthenticatedSession() {
    UUID otherUserId = UUID.randomUUID();
    given(localRegistry.getAuthenticatedSessions())
        .willReturn(Map.of("session-1", userId, "session-2", otherUserId));

    registryService.refreshHeartbeat();

    verify(zSetOperations).add(eq("ws:user-sessions:" + userId), eq("session-1"), anyDouble());
    verify(zSetOperations).add(eq("ws:user-sessions:" + otherUserId), eq("session-2"), anyDouble());
  }

  @Test
  @DisplayName("refreshHeartbeat은 로컬에 인증된 세션이 없으면 Redis를 건드리지 않는다")
  void refreshHeartbeat_noAuthenticatedSessions_doesNothing() {
    given(localRegistry.getAuthenticatedSessions()).willReturn(Collections.emptyMap());

    registryService.refreshHeartbeat();

    verify(zSetOperations, never()).add(anyString(), any(), anyDouble());
  }

  @Test
  @DisplayName("getLiveSessionIds는 살아있는 세션만 반환하고 좀비 기록은 함께 정리한다")
  void getLiveSessionIds_returnsLiveAndCleansUpStale() {
    String key = "ws:user-sessions:" + userId;
    given(zSetOperations.rangeByScore(eq(key), anyDouble(), eq(Double.MAX_VALUE)))
        .willReturn(Set.of("session-1", "session-2"));

    Set<String> liveSessionIds = registryService.getLiveSessionIds(userId);

    assertThat(liveSessionIds).containsExactlyInAnyOrder("session-1", "session-2");
    verify(zSetOperations).removeRangeByScore(eq(key), eq(Double.NEGATIVE_INFINITY), anyDouble());
  }

  @Test
  @DisplayName("getLiveSessionIds는 Redis 조회 결과가 없으면 빈 Set을 반환한다")
  void getLiveSessionIds_noMembers_returnsEmptySet() {
    given(zSetOperations.rangeByScore(anyString(), anyDouble(), eq(Double.MAX_VALUE)))
        .willReturn(null);

    Set<String> liveSessionIds = registryService.getLiveSessionIds(userId);

    assertThat(liveSessionIds).isEmpty();
  }

  @Test
  @DisplayName("publishForceDisconnect는 강제 종료 채널로 메시지를 발행한다")
  void publishForceDisconnect_publishesToChannel() {
    registryService.publishForceDisconnect(userId, "테스트 사유");

    verify(redisTemplate).convertAndSend(
        WebSocketSessionRegistryService.FORCE_DISCONNECT_CHANNEL,
        new ForceDisconnectMessage(userId, "테스트 사유"));
  }
}
