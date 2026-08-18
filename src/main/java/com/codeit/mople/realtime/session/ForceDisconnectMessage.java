package com.codeit.mople.realtime.session;

import java.util.UUID;

// Redis Pub/Sub(WebSocketSessionRegistryService.FORCE_DISCONNECT_CHANNEL)으로 방송되는 메시지.
// 모든 인스턴스가 이 메시지를 받고, 각자 로컬에 해당 userId의 세션이 있는지 확인해 처리한다.
public record ForceDisconnectMessage(UUID userId, String reason) {}
