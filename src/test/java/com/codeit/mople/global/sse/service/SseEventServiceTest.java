package com.codeit.mople.global.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.directmessage.exception.DirectMessageErrorCode;
import com.codeit.mople.domain.directmessage.exception.DirectMessageException;
import com.codeit.mople.domain.directmessage.repository.DirectMessageRepository;
import com.codeit.mople.domain.notification.dto.response.NotificationResponse;
import com.codeit.mople.domain.notification.entity.Notification;
import com.codeit.mople.domain.notification.exception.NotificationErrorCode;
import com.codeit.mople.domain.notification.exception.NotificationException;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.event.processed.ProcessedEventRepository;
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
public class SseEventServiceTest {

  @Mock
  private SseService sseService;

  @Mock
  private ProcessedEventRepository processedEventRepository;

  @Mock
  private DirectMessageRepository directMessageRepository;

  @Mock
  private NotificationRepository notificationRepository;

  @InjectMocks
  private SseEventService sseEventService;

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
  @DisplayName("DM DTO 조회")
  class GetDirectMessageDto {

    @Test
    @DisplayName("DM DTO 조회 성공")
    void getDirectMessageDto_success() {
      // given

      // BeforeEach에서 directMessageId, directMessage, conversation, sender, receiver를 초기화

      given(directMessageRepository.findById(directMessageId))
          .willReturn(Optional.of(directMessage));

      given(directMessage.getConversation())
          .willReturn(conversation);
      given(directMessage.getSender())
          .willReturn(sender);
      given(directMessage.getReceiver())
          .willReturn(receiver);

      // when
      DirectMessageDto result =
          sseEventService.getDirectMessageDto(directMessageId);

      // then
      assertThat(result).isNotNull();

      verify(directMessageRepository).findById(directMessageId);
      verify(directMessage).getConversation();
      verify(directMessage).getSender();
      verify(directMessage).getReceiver();
    }

    @Test
    @DisplayName("DM DTO 조회 실패 - DM이 존재하지 않는 경우")
    void getDirectMessageDto_notFound() {
      // given

      // BeforeEach에서 directMessageId를 초기화

      given(directMessageRepository.findById(directMessageId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
          () -> sseEventService.getDirectMessageDto(directMessageId)
      )
          .isInstanceOf(DirectMessageException.class)
          .extracting("errorCode")
          .isEqualTo(DirectMessageErrorCode.DIRECT_MESSAGE_NOT_FOUND);

      verify(directMessageRepository).findById(directMessageId);
    }

  }

  @Nested
  @DisplayName("알림 DTO 조회")
  class GetNotificationResponse {

    @Test
    @DisplayName("알림 DTO 조회 성공")
    void getNotificationResponse_success() {
      // given

      // BeforeEach에서 notificaitonId, notification, receiver를 초기화

      given(notificationRepository.findById(notificationId))
          .willReturn(Optional.of(notification));

      given(notification.getReceiver())
          .willReturn(receiver);

      // when
      NotificationResponse result =
          sseEventService.getNotificationResponse(notificationId);

      // then
      assertThat(result).isNotNull();

      verify(notificationRepository).findById(notificationId);
      verify(notification).getReceiver();
    }

    @Test
    @DisplayName("알림 DTO 조회 실패 - 알림이 존재하지 않는 경우")
    void getNotificationResponse_notFound() {
      // given
      given(notificationRepository.findById(notificationId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
          () -> sseEventService.getNotificationResponse(notificationId)
      )
          .isInstanceOf(NotificationException.class)
          .extracting("errorCode")
          .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);

      verify(notificationRepository).findById(notificationId);
    }

  }

  @Nested
  @DisplayName("이벤트 멱등성 처리")
  class CheckAndRecordProcessedEvent {

    @Test
    @DisplayName("처리되지 않은 이벤트면 기록하고 false를 반환")
    void checkAndRecordProcessedEvent_firstEvent() {
      // given
      UUID eventId = UUID.randomUUID();

      given(processedEventRepository.insertIfAbsent(eventId))
          .willReturn(1);

      // when
      boolean result = sseEventService.checkAndRecordProcessedEvent(eventId);

      // then
      assertThat(result).isFalse();

      verify(processedEventRepository).insertIfAbsent(eventId);
    }

    @Test
    @DisplayName("이미 처리된 이벤트면 기록하지 않고 true를 반환")
    void checkAndRecordProcessedEvent_duplicateEvent() {
      // given
      UUID eventId = UUID.randomUUID();

      given(processedEventRepository.insertIfAbsent(eventId))
          .willReturn(0);

      // when
      boolean result = sseEventService.checkAndRecordProcessedEvent(eventId);

      // then
      assertThat(result).isTrue();

      verify(processedEventRepository).insertIfAbsent(eventId);
    }

  }

}
