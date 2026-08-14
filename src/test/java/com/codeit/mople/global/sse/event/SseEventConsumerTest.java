package com.codeit.mople.global.sse.event;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.conversation.entity.Conversation;
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
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.sse.service.SseService;
import java.util.Optional;
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
  private DirectMessageRepository directMessageRepository;

  @Mock
  private NotificationRepository notificationRepository;

  @InjectMocks
  private SseEventConsumer eventConsumer;

  // 공통
  private UUID receiverId;
  private User receiver;

  // DirectMessage
  private UUID directMessageId;
  private DirectMessage directMessage;

  private User sender;
  private Conversation conversation;

  // Notification
  private UUID notificationId;
  private Notification notification;

  @BeforeEach
  void setUp() {
    // 공통
    receiverId = UUID.randomUUID();
    receiver = mock(User.class);

    // DirectMessage
    directMessageId = UUID.randomUUID();
    directMessage = mock(DirectMessage.class);

    sender = mock(User.class);
    conversation = mock(Conversation.class);

    // Notification
    notificationId = UUID.randomUUID();
    notification = mock(Notification.class);
  }

  @Nested
  @DisplayName("DM 생성 이벤트")
  class DirectMessageCreated {

    @Test
    @DisplayName("DM 생성 이벤트 성공")
    void handle_success() {
      // given

      // BeforeEach에서 receiverId, directMessageId, directMessage를 초기화

      DirectMessageCreatedEvent event = new DirectMessageCreatedEvent(receiverId, directMessageId);

      given(directMessageRepository.findById(directMessageId))
          .willReturn(Optional.of(directMessage));

      given(directMessage.getConversation())
          .willReturn(conversation);
      given(directMessage.getSender())
          .willReturn(sender);
      given(directMessage.getReceiver())
          .willReturn(receiver);

      // when
      eventConsumer.handle(event);

      // then
      verify(sseService).send(eq(receiverId), eq("direct-messages"), any(DirectMessageDto.class));
    }

    @Test
    @DisplayName("DM 생성 이벤트 실패 - DirectMessage가 존재하지 않을 경우")
    void handle_fail_directMessage_notFound() {
      // given

      // BeforeEach에서 receiverId, directMessageId를 초기화

      DirectMessageCreatedEvent event = new DirectMessageCreatedEvent(receiverId, directMessageId);

      given(directMessageRepository.findById(directMessageId))
          .willReturn(Optional.empty());

      // when & then
      assertThatNoException()
          .isThrownBy(() -> eventConsumer.handle(event));

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

      NotificationCreatedEvent event = new NotificationCreatedEvent(receiverId, notificationId);

      given(notificationRepository.findById(notificationId))
          .willReturn(Optional.of(notification));

      given(notification.getReceiver())
          .willReturn(receiver);


      // when
      eventConsumer.handle(event);

      // then
      verify(sseService).send(eq(receiverId), eq("notifications"), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("알림 생성 이벤트 실패 - Notification이 존재하지 않을 경우")
    void handle_fail_notification_notFound() {
      // given
      NotificationCreatedEvent event = new NotificationCreatedEvent(receiverId, notificationId);

      given(notificationRepository.findById(notificationId))
          .willReturn(Optional.empty());

      // when & then
      assertThatNoException()
          .isThrownBy(() -> eventConsumer.handle(event));

      verifyNoInteractions(sseService);
    }

  }

}
