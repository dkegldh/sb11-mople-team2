package com.codeit.mople.domain.playlist.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.playlist.repository.PlaylistRepository;
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
public class PlaylistEventListenerTest {

  @Mock
  private PlaylistRepository playlistRepository;

  @InjectMocks
  private PlaylistEventListener eventListener;

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

      PlaylistSubscribedEvent event =
          new PlaylistSubscribedEvent(ownerId, playlistId, subscriberId, "subscriber", "playlist");

      // when
      eventListener.handle(event);

      // then
      verify(playlistRepository).increaseSubscriberCount(playlistId);
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

      PlaylistUnsubscribedEvent event =
          new PlaylistUnsubscribedEvent(playlistId, subscriberId);
      given(playlistRepository.decreaseSubscriberCount(playlistId))
          .willReturn(1);

      // when
      eventListener.handle(event);

      // then
      verify(playlistRepository).decreaseSubscriberCount(playlistId);
    }

    @Test
    @DisplayName("플레이리스트 구독 취소 이벤트 실패 - 구독자 수 감소 실패")
    void handle_fail_subscriberCountNotDecrease() {
      // given

      // BeforeEach에서 playlistId, subscriberId 초기화

      PlaylistUnsubscribedEvent event =
          new PlaylistUnsubscribedEvent(playlistId, subscriberId);

      given(playlistRepository.decreaseSubscriberCount(playlistId))
          .willReturn(0); // 구독자 수 감소 실패 시 0 반환

      // when
      eventListener.handle(event);

      // then
      verify(playlistRepository).decreaseSubscriberCount(playlistId);

    }

  }

}
