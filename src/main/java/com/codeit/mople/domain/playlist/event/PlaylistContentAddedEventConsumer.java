package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.service.NotificationCreator;
import com.codeit.mople.domain.playlist.service.PlaylistService;
import com.codeit.mople.global.event.processed.ProcessedEventRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaylistContentAddedEventConsumer {

  private final PlaylistService playlistService;

  private final NotificationCreator notificationCreator;

  private final ProcessedEventRepository processedEventRepository;

  // 멱등성 마커 기록과 구독자별 알림 생성은 각자 자체 트랜잭션(insertIfAbsent, createNotification 모두
  // 자체적으로 트랜잭션을 가짐)으로 커밋되도록 이 메서드는 의도적으로 @Transactional을 붙이지 않는다.
  // 붙이면 구독자 한 명의 실패로 전체가 롤백되어(멱등성 마커 포함) 카프카가 메시지를 재시도하다 결국
  // dead-letter로 보내버려, 정상 처리됐어야 할 다른 구독자들까지 알림을 못 받게 된다.
  @KafkaListener(topics = "${spring.kafka.topics.playlist-content-added}")
  public void handle(PlaylistContentAddedMessage message) {
    if (checkAndRecordProcessedEvent(message.eventId())) {
      return;
    }

    playlistService.getSubscriberIds(message.playlistId())
        .forEach(subscriberId -> createNotificationSafely(subscriberId, message));

    log.info("플레이리스트 콘텐츠 추가 이벤트 처리 완료: playlistId={}", message.playlistId());
  }

  // 한 구독자의 알림 생성 실패가 나머지 구독자 처리를 막지 않도록 개별적으로 예외를 흡수한다.
  private void createNotificationSafely(UUID subscriberId, PlaylistContentAddedMessage message) {
    try {
      notificationCreator.createNotification(
          subscriberId,
          "구독한 플레이리스트에 새 콘텐츠가 추가되었습니다.",
          message.playlistTitle() + "에 새 콘텐츠가 추가되었습니다.",
          NotificationType.PLAYLIST_CONTENT_ADDED
      );
    } catch (Exception e) {
      log.error("구독자 알림 생성 실패 - subscriberId: {}, playlistId: {}", subscriberId, message.playlistId(), e);
    }
  }

  private boolean checkAndRecordProcessedEvent(UUID eventId) {
    // 이미 해당 eventId가 존재하면 스킵
    int inserted = processedEventRepository.insertIfAbsent(eventId);

    if (inserted == 0) {
      log.info("이미 처리된 이벤트입니다: eventId={}", eventId);
      return true;
    }

    return false;
  }

}
