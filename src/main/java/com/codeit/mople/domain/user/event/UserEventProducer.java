package com.codeit.mople.domain.user.event;

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
public class UserEventProducer {

  private final KafkaEventPublisher eventPublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(UserSearchIndexEvent event) {
    eventPublisher.publish(
        "user-search-index-events",
        event.userId().toString(),
        event
    );
  }

}