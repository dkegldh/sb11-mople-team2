package com.codeit.mople.realtime.session;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<String>> userSessions = new ConcurrentHashMap<>();
    // removeSession 시 O(1) 역방향 조회용
    private final ConcurrentHashMap<String, UUID> sessionUsers = new ConcurrentHashMap<>();

    public void addSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
    }

    public void bindUser(UUID userId, String sessionId) {
        sessionUsers.put(sessionId, userId);
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        UUID userId = sessionUsers.remove(sessionId);
        if (userId != null) {
            Set<String> ids = userSessions.get(userId);
            if (ids != null) {
                ids.remove(sessionId);
                userSessions.computeIfPresent(userId, (k, v) -> v.isEmpty() ? null : v);
            }
        }
    }

    public void closeUserSessions(UUID userId) {
        Set<String> sessionIds = userSessions.remove(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            log.debug("강제 로그아웃 대상 WebSocket 세션 없음 - userId: {}", userId);
            return;
        }
        for (String sessionId : sessionIds) {
            sessionUsers.remove(sessionId);
            WebSocketSession wsSession = sessions.remove(sessionId);
            if (wsSession != null && wsSession.isOpen()) {
                try {
                    wsSession.close(new CloseStatus(4001, "강제 로그아웃"));
                    log.info("WebSocket 세션 강제 종료 완료 - sessionId: {}", sessionId);
                } catch (IOException e) {
                    log.warn("WebSocket 세션 강제 종료 실패 - sessionId: {}", sessionId, e);
                }
            }
        }
        log.info("강제 로그아웃 WebSocket 세션 종료 완료 - userId: {}, sessionCount: {}", userId, sessionIds.size());
    }
}
