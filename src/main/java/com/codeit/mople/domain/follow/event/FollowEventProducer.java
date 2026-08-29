package com.codeit.mople.domain.follow.event;

import com.codeit.mople.global.config.KafkaProperties;
import com.codeit.mople.global.event.KafkaEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@ConditionalOnProperty(prefix = KafkaProperties.PREFIX, name = "enabled", havingValue = "true")
public class FollowEventProducer {

  private final KafkaEventPublisher publisher;
  private final String topic;

  public FollowEventProducer(KafkaEventPublisher publisher, KafkaProperties kafkaProperties) {
    this.publisher = publisher;
    this.topic = kafkaProperties.topics().followCreated();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(FollowCreatedEvent event) {
    String key = event.followeeId().toString();

    log.debug("팔로우 생성 이벤트 발행: followId={}, eventId={}",
        event.followId(), event.eventId());
    publisher.publish(topic, key, event);
  }
}
