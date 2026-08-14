package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.global.config.KafkaProperties;
import com.codeit.mople.global.event.KafkaEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "enabled", havingValue = "true")
public class PlaylistContentAddedEventRelay {

  private final KafkaEventPublisher publisher;
  private final String topic;

  public PlaylistContentAddedEventRelay(KafkaEventPublisher publisher, KafkaProperties kafkaProperties) {
    this.publisher = publisher;
    this.topic = kafkaProperties.topics().playlistContentAdded();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void relay(PlaylistContentAddedEvent event) {
    PlaylistContentAddedMessage message = PlaylistContentAddedMessage.from(event);
    String key = message.playlistId().toString();

    log.debug("플레이리스트 콘텐츠 추가 이벤트 발행: playlistContentId={}, playlistId={}, eventId={}",
        message.playlistContentId(), message.playlistId(), message.eventId());
    publisher.publish(topic, key, message);
  }
}
