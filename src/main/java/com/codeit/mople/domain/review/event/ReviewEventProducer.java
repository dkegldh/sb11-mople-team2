package com.codeit.mople.domain.review.event;

import com.codeit.mople.global.event.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.kafka", name = "enabled", havingValue = "true")
public class ReviewEventProducer {

  private final KafkaEventPublisher kafkaEventPublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ReviewCreatedEvent event) {
    kafkaEventPublisher.publish("review-created", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ReviewUpdatedEvent event) {
    kafkaEventPublisher.publish("review-updated", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ReviewDeletedEvent event) {
    kafkaEventPublisher.publish("review-deleted", event);
  }

}
