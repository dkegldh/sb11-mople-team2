package com.codeit.mople.domain.content.event;

import com.codeit.mople.global.event.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "spring.kafka",
    name = "enabled",
    havingValue = "true"
)
public class ContentEventProducer {

  private final KafkaEventPublisher eventPublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ContentSearchIndexEvent event) {
    eventPublisher.publish(
        "content-search-index-events",
        event.contentId().toString(),
        event
    );
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ContentSearchIndexDeleteEvent event) {
    eventPublisher.publish(
        "content-search-index-events",
        event.contentId().toString(),
        event
    );
  }
}