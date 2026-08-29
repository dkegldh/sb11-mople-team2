package com.codeit.mople.global.sse.event;

import com.codeit.mople.domain.directmessage.event.DirectMessageCreatedEvent;
import com.codeit.mople.domain.notification.event.NotificationCreatedEvent;
import com.codeit.mople.global.config.KafkaProperties;
import com.codeit.mople.global.event.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = KafkaProperties.PREFIX, name = "enabled", havingValue = "true")
public class SseEventProducer {

  private final KafkaEventPublisher eventPublisher;

  private final String directMessageTopic;
  private final String notificationTopic;

  public SseEventProducer(
      KafkaEventPublisher eventPublisher,
      KafkaProperties properties
  ) {
    this.eventPublisher = eventPublisher;
    this.directMessageTopic = properties.topics().directMessageCreated();
    this.notificationTopic = properties.topics().notificationCreated();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(DirectMessageCreatedEvent event) {
    eventPublisher.publish(directMessageTopic, event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(NotificationCreatedEvent event) {
    eventPublisher.publish(notificationTopic, event);
  }

}
