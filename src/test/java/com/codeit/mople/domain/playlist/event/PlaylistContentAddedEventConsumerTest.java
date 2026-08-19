package com.codeit.mople.domain.playlist.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.service.NotificationCreator;
import com.codeit.mople.domain.playlist.service.PlaylistService;
import com.codeit.mople.global.event.processed.ProcessedEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaylistContentAddedEventConsumerTest {

  @Mock
  private PlaylistService playlistService;

  @Mock
  private NotificationCreator notificationCreator;

  @Mock
  private ProcessedEventRepository processedEventRepository;

  @InjectMocks
  private PlaylistContentAddedEventConsumer eventConsumer;

  private UUID playlistContentId;
  private UUID playlistId;
  private UUID contentId;
  private UUID subscriberA;
  private UUID subscriberB;

  @BeforeEach
  void setUp() {
    playlistContentId = UUID.randomUUID();
    playlistId = UUID.randomUUID();
    contentId = UUID.randomUUID();
    subscriberA = UUID.randomUUID();
    subscriberB = UUID.randomUUID();
  }

  @Test
  @DisplayName("플레이리스트 콘텐츠 추가 이벤트 수신 시 구독자 전원에게 알림을 생성한다")
  void handle_success() {
    // given
    UUID eventId = UUID.randomUUID();
    PlaylistContentAddedMessage message = new PlaylistContentAddedMessage(
        eventId, Instant.now(), playlistContentId, playlistId, contentId, "테스트 플레이리스트"
    );

    given(processedEventRepository.insertIfAbsent(eventId)).willReturn(1);
    given(playlistService.getSubscriberIds(playlistId))
        .willReturn(List.of(subscriberA, subscriberB));

    // when
    eventConsumer.handle(message);

    // then
    verify(processedEventRepository).insertIfAbsent(eventId);
    verify(notificationCreator).createNotification(
        subscriberA,
        "구독한 플레이리스트에 새 콘텐츠가 추가되었습니다.",
        "테스트 플레이리스트에 새 콘텐츠가 추가되었습니다.",
        NotificationType.PLAYLIST_CONTENT_ADDED
    );
    verify(notificationCreator).createNotification(
        subscriberB,
        "구독한 플레이리스트에 새 콘텐츠가 추가되었습니다.",
        "테스트 플레이리스트에 새 콘텐츠가 추가되었습니다.",
        NotificationType.PLAYLIST_CONTENT_ADDED
    );
  }

  @Test
  @DisplayName("이미 처리된 이벤트는 알림을 생성하지 않고 스킵한다")
  void handle_success_already_processed_skip() {
    // given
    UUID eventId = UUID.randomUUID();
    PlaylistContentAddedMessage message = new PlaylistContentAddedMessage(
        eventId, Instant.now(), playlistContentId, playlistId, contentId, "테스트 플레이리스트"
    );

    given(processedEventRepository.insertIfAbsent(eventId)).willReturn(0);

    // when
    eventConsumer.handle(message);

    // then
    verify(processedEventRepository).insertIfAbsent(eventId);
    verifyNoInteractions(playlistService);
    verifyNoInteractions(notificationCreator);
  }
}
