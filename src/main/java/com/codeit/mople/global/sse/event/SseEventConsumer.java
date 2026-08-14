package com.codeit.mople.global.sse.event;

import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.directmessage.event.DirectMessageCreatedEvent;
import com.codeit.mople.domain.directmessage.exception.DirectMessageErrorCode;
import com.codeit.mople.domain.directmessage.exception.DirectMessageException;
import com.codeit.mople.domain.directmessage.repository.DirectMessageRepository;
import com.codeit.mople.domain.notification.dto.response.NotificationResponse;
import com.codeit.mople.domain.notification.entity.Notification;
import com.codeit.mople.domain.notification.event.NotificationCreatedEvent;
import com.codeit.mople.domain.notification.exception.NotificationErrorCode;
import com.codeit.mople.domain.notification.exception.NotificationException;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import com.codeit.mople.global.sse.service.SseService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventConsumer {

  private final SseService sseService;
  private final DirectMessageRepository directMessageRepository;
  private final NotificationRepository notificationRepository;

  @KafkaListener(topics = "direct-message-created")
  @Transactional(readOnly = true)
  public void handle(DirectMessageCreatedEvent event) {
    log.debug("SSE 이벤트 전송 시도: receiverId={}, directMessageId={}",
        event.receiverId(), event.directMessageId());

    DirectMessage directMessage = directMessageRepository.findById(event.directMessageId())
        .orElse(null);

    if (directMessage == null) {
      log.warn("SSE 전송 대상 DM을 찾을 수 없습니다: directMessageId={}, receiverId={}",
          event.directMessageId(), event.receiverId());
      return;
    }

    DirectMessageDto directMessageDto = DirectMessageDto.from(directMessage);

    // SSE 이벤트 전송
    // RuntimeException, CustomException, IOException 등 다양한 예외가 발생할 수 있기 때문에 Exception으로 설정
    // 대신 로그 메시지에 stackTrace를 확인하여 어떤 예외가 발생했는지 알 수 있도록 설정
    try {
      sseService.send(
          event.receiverId(),
          "direct-messages",
          directMessageDto
      );

      log.info("SSE 전송 완료: receiverId={}, directMessageId={}",
          event.receiverId(), event.directMessageId());
    } catch (Exception e) {
      log.error("SSE 전송 실패: receiverId={}, directMessageId={}",
          event.receiverId(), event.directMessageId(), e);
    }
  }

  @KafkaListener(topics = "notification-created")
  @Transactional(readOnly = true)
  public void handle(NotificationCreatedEvent event) {
    log.debug("SSE 이벤트 전송 시도: receiverId={}, notificationId={}",
        event.receiverId(), event.notificationId());

    Notification notification = notificationRepository.findById(event.notificationId())
        .orElse(null);

    if (notification == null) {
      log.warn("SSE 전송 대상 알림을 찾을 수 없습니다: notificationId={}, receiverId={}",
          event.notificationId(), event.receiverId());
      return;
    }

    NotificationResponse response = NotificationResponse.from(notification);

    try {
      sseService.send(
          event.receiverId(),
          "notifications",
          response
      );

      log.info("알림 SSE 전송 완료: receiverId={}, notificationId={}",
          event.receiverId(), event.notificationId());
    } catch (Exception e) {
      log.error("알림 SSE 전송 실패: receiverId={}, notificationId={}",
          event.receiverId(), event.notificationId(), e);
    }
  }

}
