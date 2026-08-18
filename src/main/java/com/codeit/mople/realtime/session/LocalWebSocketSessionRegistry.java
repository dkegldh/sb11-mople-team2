package com.codeit.mople.realtime.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

// 이 JVM 인스턴스가 실제로 들고 있는 WebSocketSession을 sessionId 기준으로 추적한다.
// Redis(ws:user-sessions)는 "어떤 세션이 존재한다"는 사실만 알 뿐, 그 세션의 실제
// WebSocketSession 객체는 이 프로세스의 메모리에만 있어서 다른 인스턴스가 대신 닫아줄 수
// 없다 - 그래서 로컬 레지스트리가 별도로 필요하다.
@Slf4j
@Component
public class LocalWebSocketSessionRegistry {

  private record SessionEntry(WebSocketSession session, UUID userId) {}

  private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

  // 핸드셰이크 직후(STOMP CONNECT 이전) 호출 - 아직 인증 전이라 userId는 비워둠
  public void registerConnection(WebSocketSession session) {
    sessions.put(session.getId(), new SessionEntry(session, null));
  }

  // CONNECT 인증 성공 시 호출 - 앞서 등록된 연결에 유저를 바인딩
  public void bindUser(String sessionId, UUID userId) {
    SessionEntry updated = sessions.computeIfPresent(sessionId,
        (id, entry) -> new SessionEntry(entry.session(), userId));
    if (updated == null) {
      log.warn("WebSocket 세션 유저 바인딩 실패(핸드셰이크 등록 이력 없음) - sessionId: {}, userId: {}",
          sessionId, userId);
    }
  }

  // 연결 종료(정상/비정상/강제종료 공통 콜백) 시 호출 - 바인딩돼 있었다면 그 userId를 반환
  public Optional<UUID> removeConnection(String sessionId) {
    SessionEntry removed = sessions.remove(sessionId);
    return removed == null ? Optional.empty() : Optional.ofNullable(removed.userId());
  }

  public Optional<WebSocketSession> getSession(String sessionId) {
    return Optional.ofNullable(sessions.get(sessionId)).map(SessionEntry::session);
  }

  // 강제 종료 대상 탐색용 - 주어진 유저의 세션 중 이 인스턴스가 들고 있는 것만 반환
  public Set<String> getSessionIdsForUser(UUID userId) {
    return sessions.entrySet().stream()
        .filter(entry -> userId.equals(entry.getValue().userId()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  // 하트비트 스케줄러용 - 인증까지 완료된(userId가 바인딩된) 세션만 대상
  public Map<String, UUID> getAuthenticatedSessions() {
    Map<String, UUID> result = new HashMap<>();
    sessions.forEach((sessionId, entry) -> {
      if (entry.userId() != null) {
        result.put(sessionId, entry.userId());
      }
    });
    return result;
  }
}
