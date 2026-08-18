package com.codeit.mople.realtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketForceDisconnectListener")
class WebSocketForceDisconnectListenerTest {

  @InjectMocks
  private WebSocketForceDisconnectListener listener;

  @Mock
  private RedisTemplate<String, Object> redisTemplate;

  @Mock
  private LocalWebSocketSessionRegistry localRegistry;

  @Mock
  private SimpMessagingTemplate messagingTemplate;

  @Mock
  private RedisSerializer<Object> valueSerializer;

  @Mock
  private Message message;

  private final UUID userId = UUID.randomUUID();
  private final byte[] body = new byte[]{1, 2, 3};

  private void givenPayload(Object payload) {
    given(message.getBody()).willReturn(body);
    doReturn(valueSerializer).when(redisTemplate).getValueSerializer();
    given(valueSerializer.deserialize(body)).willReturn(payload);
  }

  @Test
  @DisplayName("로컬에 있는 세션에 사유를 알린 뒤 종료한다")
  void validPayload_notifiesAndClosesLocalSessions() throws IOException {
    WebSocketSession session = mock(WebSocketSession.class);
    givenPayload(new ForceDisconnectMessage(userId, "테스트 사유"));
    given(localRegistry.getSessionIdsForUser(userId)).willReturn(Set.of("session-1"));
    given(localRegistry.getSession("session-1")).willReturn(Optional.of(session));

    listener.onMessage(message, null);

    verify(messagingTemplate).convertAndSendToUser(
        userId.toString(), "/queue/errors", Map.of("reason", "테스트 사유"));
    ArgumentCaptor<CloseStatus> closeStatusCaptor = ArgumentCaptor.forClass(CloseStatus.class);
    verify(session).close(closeStatusCaptor.capture());
    assertThat(closeStatusCaptor.getValue().getCode()).isEqualTo(4001);
  }

  @Test
  @DisplayName("로컬에 해당 유저의 세션이 없으면 아무 것도 하지 않는다")
  void validPayload_noLocalSessions_doesNothing() {
    givenPayload(new ForceDisconnectMessage(userId, "테스트 사유"));
    given(localRegistry.getSessionIdsForUser(userId)).willReturn(Collections.emptySet());

    listener.onMessage(message, null);

    verify(localRegistry, never()).getSession(any());
    verifyNoInteractions(messagingTemplate);
  }

  @Test
  @DisplayName("세션이 여러 개여도 알림은 1번만 보내고 모든 세션을 닫는다")
  void validPayload_multipleSessions_notifiesOnceAndClosesAll() throws IOException {
    WebSocketSession session1 = mock(WebSocketSession.class);
    WebSocketSession session2 = mock(WebSocketSession.class);
    givenPayload(new ForceDisconnectMessage(userId, "테스트 사유"));
    given(localRegistry.getSessionIdsForUser(userId)).willReturn(Set.of("session-1", "session-2"));
    given(localRegistry.getSession("session-1")).willReturn(Optional.of(session1));
    given(localRegistry.getSession("session-2")).willReturn(Optional.of(session2));

    listener.onMessage(message, null);

    verify(messagingTemplate, times(1)).convertAndSendToUser(
        userId.toString(), "/queue/errors", Map.of("reason", "테스트 사유"));
    verify(session1).close(any());
    verify(session2).close(any());
  }

  @Test
  @DisplayName("세션 close 중 IOException이 발생해도 예외를 전파하지 않는다")
  void validPayload_closeThrowsIOException_doesNotPropagate() throws IOException {
    WebSocketSession session = mock(WebSocketSession.class);
    givenPayload(new ForceDisconnectMessage(userId, "테스트 사유"));
    given(localRegistry.getSessionIdsForUser(userId)).willReturn(Set.of("session-1"));
    given(localRegistry.getSession("session-1")).willReturn(Optional.of(session));
    doThrow(new IOException("close 실패")).when(session).close(any());

    listener.onMessage(message, null);

    verify(session).close(any());
  }

  @Test
  @DisplayName("알 수 없는 형식의 payload면 무시하고 종료를 위임하지 않는다")
  void unknownPayload_doesNotDelegate() {
    givenPayload("예상치 못한 문자열 payload");

    listener.onMessage(message, null);

    verifyNoInteractions(localRegistry);
    verifyNoInteractions(messagingTemplate);
  }
}
