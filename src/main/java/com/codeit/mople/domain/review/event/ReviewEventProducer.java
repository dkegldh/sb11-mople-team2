package com.codeit.mople.domain.review.event;

import com.codeit.mople.global.config.KafkaProperties;
import com.codeit.mople.global.event.KafkaEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = KafkaProperties.PREFIX, name = "enabled", havingValue = "true")
public class ReviewEventProducer {

  private final KafkaEventPublisher eventPublisher;

  private final String reviewCreatedTopic;
  private final String reviewUpdatedTopic;
  private final String reviewDeletedTopic;

  public ReviewEventProducer(
      KafkaEventPublisher eventPublisher,
      KafkaProperties properties
  ) {
    this.eventPublisher = eventPublisher;
    this.reviewCreatedTopic = properties.topics().reviewCreated();
    this.reviewUpdatedTopic = properties.topics().reviewUpdated();
    this.reviewDeletedTopic = properties.topics().reviewDeleted();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ReviewCreatedEvent event) {
    eventPublisher.publish(reviewCreatedTopic, event.eventId().toString(), event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ReviewUpdatedEvent event) {
    eventPublisher.publish(reviewUpdatedTopic, event.eventId().toString(), event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(ReviewDeletedEvent event) {
    eventPublisher.publish(reviewDeletedTopic, event.eventId().toString(), event);
  }

}
