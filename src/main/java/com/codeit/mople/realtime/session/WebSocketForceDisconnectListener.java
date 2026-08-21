package com.codeit.mople.realtime.session;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

// WebSocketSessionRegistryService.FORCE_DISCONNECT_CHANNEL 구독자.
// 모든 인스턴스가 동일한 메시지를 받지만, 실제 종료는 각자 로컬에 해당 세션을
// 들고 있는 인스턴스에서만 일어난다
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketForceDisconnectListener implements MessageListener {

  private final RedisTemplate<String, Object> redisTemplate;
  private final LocalWebSocketSessionRegistry localRegistry;
  private final SimpMessagingTemplate messagingTemplate;

  private static final String ERROR_DESTINATION = "/queue/errors";

  // convertAndSendToUser는 아웃바운드 채널(기본적으로 비동기 스레드풀)에 전달을 위임하고
  // 바로 리턴하므로, 리턴 시점에 메시지가 실제로 소켓에 다 쓰였다는 보장이 없다. close()를
  // 곧바로 이어 붙이면 사유 메시지가 도착하기 전에 연결이 끊길 수 있어,
  // 짧은 지연을 두어 전송이 끝날 시간을 확보한다.
  private static final long NOTIFY_BEFORE_CLOSE_DELAY_MS = 100;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    Object payload = redisTemplate.getValueSerializer().deserialize(message.getBody());

    if (!(payload instanceof ForceDisconnectMessage forceDisconnectMessage)) {
      log.error("강제 종료 메시지 역직렬화 실패(강제 로그아웃이 조용히 무시됩니다) - "
          + "payloadType: {}, payload: {}",
          payload == null ? "null" : payload.getClass().getName(), payload);
      return;
    }

    log.debug("WebSocket 강제 종료 신호 수신 - userId: {}", forceDisconnectMessage.userId());
    closeLocalSessions(forceDisconnectMessage.userId(), forceDisconnectMessage.reason());
  }

  // 이 인스턴스가 실제로 들고 있는 세션에 대해서만 알림 후 종료를 수행
  private void closeLocalSessions(UUID userId, String reason) {
    Set<String> sessionIds = localRegistry.getSessionIdsForUser(userId);
    List<WebSocketSession> targets = sessionIds.stream()
        .map(localRegistry::getSession)
        .flatMap(Optional::stream)
        .toList();
    if (targets.isEmpty()) {
      return;
    }

    messagingTemplate.convertAndSendToUser(userId.toString(), ERROR_DESTINATION,
        Map.of("reason", reason));

    try {
      Thread.sleep(NOTIFY_BEFORE_CLOSE_DELAY_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();

      log.warn("WebSocket 강제 종료 대기 중 인터럽트 발생 - userId: {}", userId);
    }

    targets.forEach(session -> closeSession(userId, session));
  }

  private void closeSession(UUID userId, WebSocketSession session) {
    try {
      session.close(new CloseStatus(4001, "FORCE_LOGOUT"));
      log.info("WebSocket 강제 종료 완료 - userId: {}, sessionId: {}", userId, session.getId());
    } catch (IOException e) {
      log.warn("WebSocket 강제 종료 중 오류 발생 - userId: {}, sessionId: {}", userId, session.getId(), e);
    }
  }
}
