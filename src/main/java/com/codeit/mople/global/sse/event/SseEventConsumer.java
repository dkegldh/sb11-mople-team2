package com.codeit.mople.global.sse.event;

import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.event.DirectMessageCreatedEvent;
import com.codeit.mople.domain.notification.dto.response.NotificationResponse;
import com.codeit.mople.domain.notification.event.NotificationCreatedEvent;
import com.codeit.mople.global.sse.service.SseEventService;
import com.codeit.mople.global.sse.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventConsumer {

  private final SseService sseService;
  private final SseEventService sseEventService;

  @KafkaListener(topics = "direct-message-created")
  public void handle(DirectMessageCreatedEvent event) {

    log.debug("SSE 이벤트 전송 시도: receiverId={}, directMessageId={}",
        event.receiverId(), event.directMessageId());

    DirectMessageDto response =
        sseEventService.getDirectMessageDto(event.directMessageId());

    if (sseEventService.checkAndRecordProcessedEvent(event.eventId())) {
      return;
    }

    // SSE 이벤트 전송
    sseService.send(
        event.receiverId(),
        "direct-messages",
        response
    );

    log.info("SSE 전송 완료: receiverId={}, directMessageId={}",
        event.receiverId(), event.directMessageId());
  }

  @KafkaListener(topics = "notification-created")
  public void handle(NotificationCreatedEvent event) {

    log.debug("SSE 이벤트 전송 시도: receiverId={}, notificationId={}",
        event.receiverId(), event.notificationId());

    NotificationResponse response =
        sseEventService.getNotificationResponse(event.notificationId());

    if (sseEventService.checkAndRecordProcessedEvent(event.eventId())) {
      return;
    }

    sseService.send(
        event.receiverId(),
        "notifications",
        response
    );

    log.info("알림 SSE 전송 완료: receiverId={}, notificationId={}",
        event.receiverId(), event.notificationId());
  }

}
