package com.codeit.mople.domain.follow.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.service.NotificationCreator;
import com.codeit.mople.global.event.processed.ProcessedEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowEventConsumerTest {

  @Mock
  private NotificationCreator notificationCreator;

  @Mock
  private ProcessedEventRepository processedEventRepository;

  @InjectMocks
  private FollowEventConsumer eventConsumer;

  private UUID followId;
  private UUID followeeId;
  private UUID followerId;

  @BeforeEach
  void setUp() {
    followId = UUID.randomUUID();
    followeeId = UUID.randomUUID();
    followerId = UUID.randomUUID();
  }

  @Test
  @DisplayName("팔로우 생성 이벤트 수신 시 알림을 생성한다")
  void handle_success() {
    // given
    UUID eventId = UUID.randomUUID();
    FollowCreatedMessage message = new FollowCreatedMessage(
        eventId, Instant.now(), followId, followeeId, followerId, "팔로워"
    );

    given(processedEventRepository.insertIfAbsent(eventId)).willReturn(1);

    // when
    eventConsumer.handle(message);

    // then
    verify(processedEventRepository).insertIfAbsent(eventId);
    verify(notificationCreator).createNotification(
        followeeId,
        "새로운 팔로워가 생겼습니다.",
        "팔로워님이 팔로우했습니다.",
        NotificationType.NEW_FOLLOWER
    );
  }

  @Test
  @DisplayName("이미 처리된 이벤트는 알림을 생성하지 않고 스킵한다")
  void handle_success_already_processed_skip() {
    // given
    UUID eventId = UUID.randomUUID();
    FollowCreatedMessage message = new FollowCreatedMessage(
        eventId, Instant.now(), followId, followeeId, followerId, "팔로워"
    );

    given(processedEventRepository.insertIfAbsent(eventId)).willReturn(0);

    // when
    eventConsumer.handle(message);

    // then
    verify(processedEventRepository).insertIfAbsent(eventId);
    verifyNoInteractions(notificationCreator);
  }
}
