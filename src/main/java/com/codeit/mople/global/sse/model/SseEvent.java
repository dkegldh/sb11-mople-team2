package com.codeit.mople.global.sse.model;

import java.util.UUID;

public record SseEvent (
    UUID id, // SSE 이벤트 고유 ID
    UUID receiverId, // SSE 이벤트 주인
    String eventName, // 이벤트 이름(direct-messages, notifications 등)
    Object data // 실제 이벤트 정보
) {

}
