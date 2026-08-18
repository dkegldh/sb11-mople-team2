package com.codeit.mople.global.sse.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.event.DirectMessageCreatedEvent;
import com.codeit.mople.domain.notification.dto.response.NotificationResponse;
import com.codeit.mople.domain.notification.event.NotificationCreatedEvent;
import com.codeit.mople.global.sse.service.SseEventService;
import com.codeit.mople.global.sse.service.SseService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SseEventConsumerTest {

  @Mock
  private SseService sseService;

  @Mock
  private SseEventService sseEventService;

  @InjectMocks
  private SseEventConsumer eventConsumer;

  private UUID receiverId;
  private UUID directMessageId;
  private UUID notificationId;

  @BeforeEach
  void setUp() {
    // 공통
    receiverId = UUID.randomUUID();
    directMessageId = UUID.randomUUID();
    notificationId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("DM 생성 이벤트")
  class DirectMessageCreated {

    @Test
    @DisplayName("DM 생성 이벤트 성공")
    void handle_success() {
      // given

      // BeforeEach에서 receiverId, directMessageId, directMessage를 초기화

      UUID eventId = UUID.randomUUID();

      DirectMessageCreatedEvent event =
          new DirectMessageCreatedEvent(eventId, receiverId, directMessageId);

      DirectMessageDto directMessageDto = mock(DirectMessageDto.class);

      given(sseEventService.getDirectMessageDto(directMessageId))
          .willReturn(directMessageDto);

      given(sseEventService.checkAndRecordProcessedEvent(eventId))
          .willReturn(false);

      // when
      eventConsumer.handle(event);

      // then
      verify(sseEventService).getDirectMessageDto(directMessageId);
      verify(sseEventService).checkAndRecordProcessedEvent(eventId);
      verify(sseService).send(eq(receiverId), eq("direct-messages"), any(DirectMessageDto.class));
    }

    @Test
    @DisplayName("DM 생성 이벤트 실패 - 이미 처리된 이벤트일 경우 SSE를 전송하지 않음")
    void handle_fail_duplicate() {
      // given

      // BeforeEach에서 receiverId, directMessageId, directMessage를 초기화

      UUID eventId = UUID.randomUUID();

      DirectMessageCreatedEvent event =
          new DirectMessageCreatedEvent(eventId, receiverId, directMessageId);

      DirectMessageDto directMessageDto = mock(DirectMessageDto.class);

      given(sseEventService.getDirectMessageDto(directMessageId))
          .willReturn(directMessageDto);

      given(sseEventService.checkAndRecordProcessedEvent(eventId))
          .willReturn(true);

      // when
      eventConsumer.handle(event);

      // then
      verify(sseEventService).getDirectMessageDto(directMessageId);
      verify(sseEventService).checkAndRecordProcessedEvent(eventId);

      verifyNoInteractions(sseService);
    }

  }

  @Nested
  @DisplayName("알림 생성 이벤트")
  class NotificationCreated {

    @Test
    @DisplayName("알림 생성 이벤트 성공")
    void handle_success() {
      // given

      // BeforeEach에서 receiverId, notificationId, notification를 초기화

      UUID eventId = UUID.randomUUID();

      NotificationCreatedEvent event =
          new NotificationCreatedEvent(eventId, receiverId, notificationId);

      NotificationResponse notificationResponse = mock(NotificationResponse.class);

      given(sseEventService.getNotificationResponse(notificationId))
          .willReturn(notificationResponse);

      given(sseEventService.checkAndRecordProcessedEvent(eventId))
          .willReturn(false);

      // when
      eventConsumer.handle(event);

      // then
      verify(sseEventService).getNotificationResponse(notificationId);
      verify(sseEventService).checkAndRecordProcessedEvent(eventId);
      verify(sseService).send(eq(receiverId), eq("notifications"), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("알림 생성 이벤트 실패 - 이미 처리된 이벤트일 경우 SSE를 전송하지 않음")
    void handle_fail_duplicate() {
      // given

      // BeforeEach에서 receiverId, notificationId, notification를 초기화

      UUID eventId = UUID.randomUUID();

      NotificationCreatedEvent event =
          new NotificationCreatedEvent(eventId, receiverId, notificationId);

      NotificationResponse notificationResponse = mock(NotificationResponse.class);

      given(sseEventService.getNotificationResponse(notificationId))
          .willReturn(notificationResponse);

      given(sseEventService.checkAndRecordProcessedEvent(eventId))
          .willReturn(true);

      // when
      eventConsumer.handle(event);

      // then
      verify(sseEventService).getNotificationResponse(notificationId);
      verify(sseEventService).checkAndRecordProcessedEvent(eventId);

      verifyNoInteractions(sseService);
    }

  }

}
