package com.codeit.mople.realtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

@DisplayName("LocalWebSocketSessionRegistry")
class LocalWebSocketSessionRegistryTest {

  private LocalWebSocketSessionRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new LocalWebSocketSessionRegistry();
  }

  private WebSocketSession mockSession(String sessionId) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(sessionId);
    return session;
  }

  @Test
  @DisplayName("연결 등록 후 바인딩 전에는 유저의 세션 목록에 잡히지 않는다")
  void registerConnection_beforeBindUser_notVisibleByUser() {
    WebSocketSession session = mockSession("session-1");
    UUID userId = UUID.randomUUID();

    registry.registerConnection(session);

    assertThat(registry.getSession("session-1")).contains(session);
    assertThat(registry.getSessionIdsForUser(userId)).isEmpty();
    assertThat(registry.getAuthenticatedSessions()).isEmpty();
  }

  @Test
  @DisplayName("bindUser 이후에는 해당 유저의 세션 목록과 인증된 세션 목록에 포함된다")
  void bindUser_afterRegisterConnection_visibleByUser() {
    WebSocketSession session = mockSession("session-1");
    UUID userId = UUID.randomUUID();

    registry.registerConnection(session);
    registry.bindUser("session-1", userId);

    assertThat(registry.getSessionIdsForUser(userId)).containsExactly("session-1");
    assertThat(registry.getAuthenticatedSessions()).containsExactly(Map.entry("session-1", userId));
  }

  @Test
  @DisplayName("registerConnection 없이 bindUser만 호출하면 아무 효과가 없다 (등록 안 된 세션)")
  void bindUser_withoutRegisterConnection_isNoOp() {
    UUID userId = UUID.randomUUID();

    registry.bindUser("unknown-session", userId);

    assertThat(registry.getSessionIdsForUser(userId)).isEmpty();
    assertThat(registry.getSession("unknown-session")).isEmpty();
  }

  @Test
  @DisplayName("removeConnection은 바인딩됐던 userId를 반환하고 로컬 레지스트리에서 완전히 제거한다")
  void removeConnection_boundSession_returnsUserIdAndClears() {
    WebSocketSession session = mockSession("session-1");
    UUID userId = UUID.randomUUID();
    registry.registerConnection(session);
    registry.bindUser("session-1", userId);

    Optional<UUID> removedUserId = registry.removeConnection("session-1");

    assertThat(removedUserId).contains(userId);
    assertThat(registry.getSession("session-1")).isEmpty();
    assertThat(registry.getSessionIdsForUser(userId)).isEmpty();
  }

  @Test
  @DisplayName("removeConnection은 인증 전(userId 미바인딩) 세션이면 빈 Optional을 반환한다")
  void removeConnection_unauthenticatedSession_returnsEmpty() {
    WebSocketSession session = mockSession("session-1");
    registry.registerConnection(session);

    Optional<UUID> removedUserId = registry.removeConnection("session-1");

    assertThat(removedUserId).isEmpty();
    assertThat(registry.getSession("session-1")).isEmpty();
  }

  @Test
  @DisplayName("removeConnection은 존재하지 않는 sessionId에 대해 빈 Optional을 반환한다")
  void removeConnection_unknownSession_returnsEmpty() {
    Optional<UUID> removedUserId = registry.removeConnection("never-registered");

    assertThat(removedUserId).isEmpty();
  }

  @Test
  @DisplayName("getSessionIdsForUser는 멀티탭(같은 유저의 여러 세션)을 모두 반환하고 다른 유저 세션은 제외한다")
  void getSessionIdsForUser_multiTabAndOtherUsers() {
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    registry.registerConnection(mockSession("a-1"));
    registry.bindUser("a-1", userA);
    registry.registerConnection(mockSession("a-2"));
    registry.bindUser("a-2", userA);
    registry.registerConnection(mockSession("b-1"));
    registry.bindUser("b-1", userB);

    Set<String> sessionIdsForUserA = registry.getSessionIdsForUser(userA);

    assertThat(sessionIdsForUserA).containsExactlyInAnyOrder("a-1", "a-2");
  }
}
