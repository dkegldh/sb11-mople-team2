package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.domain.playlist.repository.PlaylistRepository;
import com.codeit.mople.global.event.processed.ProcessedEventRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = "playlist-events")
public class PlaylistEventConsumer {

  private final PlaylistRepository playlistRepository;

  private final ProcessedEventRepository processedEventRepository;

  @KafkaHandler
  @Transactional
  public void handle(PlaylistSubscribedEvent event) {
    if (checkAndRecordProcessedEvent(event.eventId())) {
      return;
    }

    playlistRepository.increaseSubscriberCount(event.playlistId());

    log.info("플레이리스트 구독자 수 증가 완료: playlistId={}",
        event.playlistId());
  }

  @KafkaHandler
  @Transactional
  public void handle(PlaylistUnsubscribedEvent event) {
    if (checkAndRecordProcessedEvent(event.eventId())) {
      return;
    }

    int decreased = playlistRepository.decreaseSubscriberCount(event.playlistId());
    if (decreased == 0) {
      log.warn("구독자 수 감소 실패(이미 0): playlistId={}, subscriberId={}",
          event.playlistId(), event.subscriberId());

      return;
    }
    log.info("플레이리스트 구독자 수 감소 완료: playlistId={}",
        event.playlistId());
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
