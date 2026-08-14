package com.codeit.mople.domain.playlist.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.playlist.repository.PlaylistRepository;
import com.codeit.mople.global.event.processed.ProcessedEventRepository;
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
public class PlaylistEventConsumerTest {

  @Mock
  private PlaylistRepository playlistRepository;

  @Mock
  private ProcessedEventRepository processedEventRepository;

  @InjectMocks
  private PlaylistEventConsumer eventConsumer;

  private UUID ownerId;
  private UUID playlistId;
  private UUID subscriberId;

  @BeforeEach
  void setUp() {
    ownerId = UUID.randomUUID();
    playlistId = UUID.randomUUID();
    subscriberId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("플레이리스트 구독 이벤트")
  class SubscribedEvent {

    @Test
    @DisplayName("플레이리스트 구독 이벤트 성공")
    void handle_success() {
      // given

      // BeforeEach에서 ownerId, playlistId, subscriberId 초기화

      UUID eventId = UUID.randomUUID();

      PlaylistSubscribedEvent event =
          new PlaylistSubscribedEvent(
              eventId,
              ownerId,
              playlistId,
              subscriberId,
              "subscriber",
              "playlist"
          );

      given(processedEventRepository.insertIfAbsent(eventId))
          .willReturn(1);

      given(playlistRepository.increaseSubscriberCount(playlistId))
          .willReturn(1);

      // when
      eventConsumer.handle(event);

      // then
      verify(processedEventRepository).insertIfAbsent(eventId);
      verify(playlistRepository).increaseSubscriberCount(playlistId);
    }

    @Test
    @DisplayName("플레이리스트 구독 이벤트 성공 - 이미 처리된 구독 이벤트는 스킵")
    void handle_success_already_processed_skip() {
      // given

      // BeforeEach에서 ownerId, playlistId, subscriberId 초기화

      UUID eventId = UUID.randomUUID();

      PlaylistSubscribedEvent event =
          new PlaylistSubscribedEvent(
              eventId,
              ownerId,
              playlistId,
              subscriberId,
              "subscriber",
              "playlist"
          );

      given(processedEventRepository.insertIfAbsent(eventId))
          .willReturn(0);

      // when
      eventConsumer.handle(event);

      // then
      verify(processedEventRepository).insertIfAbsent(eventId);
      verifyNoInteractions(playlistRepository);
    }

  }

  @Nested
  @DisplayName("플레이리스트 구독 취소 이벤트")
  class UnsubscribedEvent {

    @Test
    @DisplayName("플레이리스트 구독 취소 이벤트 성공")
    void handle_success() {
      // given

      // BeforeEach에서 playlistId, subscriberId 초기화

      UUID eventId = UUID.randomUUID();

      PlaylistUnsubscribedEvent event =
          new PlaylistUnsubscribedEvent(eventId, playlistId, subscriberId);

      given(processedEventRepository.insertIfAbsent(eventId))
          .willReturn(1);

      given(playlistRepository.decreaseSubscriberCount(playlistId))
          .willReturn(1);

      // when
      eventConsumer.handle(event);

      // then
      verify(processedEventRepository).insertIfAbsent(eventId);
      verify(playlistRepository).decreaseSubscriberCount(playlistId);
    }

    @Test
    @DisplayName("플레이리스트 구독 취소 이벤트 성공 - 이미 처리된 구독 이벤트는 스킵")
    void handle_success_already_processed_skip() {
      // given
      UUID eventId = UUID.randomUUID();

      PlaylistUnsubscribedEvent event =
          new PlaylistUnsubscribedEvent(
              eventId,
              playlistId,
              subscriberId
          );

      given(processedEventRepository.insertIfAbsent(eventId))
          .willReturn(0);

      // when
      eventConsumer.handle(event);

      // then
      verify(processedEventRepository).insertIfAbsent(eventId);
      verifyNoInteractions(playlistRepository);
    }

    @Test
    @DisplayName("플레이리스트 구독 취소 이벤트 실패 - 구독자 수 감소 실패")
    void handle_fail_subscriberCountNotDecrease() {
      // given

      // BeforeEach에서 playlistId, subscriberId 초기화

      UUID eventId = UUID.randomUUID();

      PlaylistUnsubscribedEvent event =
          new PlaylistUnsubscribedEvent(eventId, playlistId, subscriberId);

      given(processedEventRepository.insertIfAbsent(eventId))
          .willReturn(1);

      given(playlistRepository.decreaseSubscriberCount(playlistId))
          .willReturn(0); // 구독자 수 감소 실패 시 0 반환

      // when
      eventConsumer.handle(event);

      // then
      verify(processedEventRepository).insertIfAbsent(eventId);
      verify(playlistRepository).decreaseSubscriberCount(playlistId);

    }

  }

}
