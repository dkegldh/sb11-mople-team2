package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.global.event.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.kafka", name = "enabled", havingValue = "true")
public class PlaylistEventProducer {

  private final KafkaEventPublisher eventPublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(PlaylistSubscribedEvent event) {
    eventPublisher.publish("playlist-events", event.playlistId().toString(), event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(PlaylistUnsubscribedEvent event) {
    eventPublisher.publish("playlist-events", event.playlistId().toString(), event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(PlaylistSearchIndexEvent event) {
    eventPublisher.publish("playlist-search-index-events", event.playlistId().toString(), event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(PlaylistSearchIndexDeleteEvent event) {
    eventPublisher.publish(
        "playlist-search-index-events",
        event.playlistId().toString(),
        event
    );
  }

}
