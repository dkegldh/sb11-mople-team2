package com.codeit.mople.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.notification.entity.Notification;
import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.event.NotificationCreatedEvent;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NotificationCreatorTest {

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private NotificationCreator notificationCreator;

  @Nested
  @DisplayName("알림 생성")
  class CreateNotification {

    @Test
    @DisplayName("알림 생성 성공")
    void createNotification_success() {
      // given
      UUID receiverId = UUID.randomUUID();
      UUID notificationId = UUID.randomUUID();

      User receiver = mock(User.class);
      Notification notification = mock(Notification.class);

      given(userRepository.findById(receiverId))
          .willReturn(Optional.of(receiver));

      given(notificationRepository.save(any(Notification.class)))
          .willReturn(notification);

      given(notification.getId())
          .willReturn(notificationId);

      // when
      notificationCreator.createNotification(
          receiverId,
          "새로운 메시지",
          "새로운 메시지가 도착했습니다.",
          NotificationType.DIRECT_MESSAGE
      );

      // then
      verify(notificationRepository)
          .save(any(Notification.class));

      verify(eventPublisher)
          .publishEvent(any(NotificationCreatedEvent.class));
    }
  }
}