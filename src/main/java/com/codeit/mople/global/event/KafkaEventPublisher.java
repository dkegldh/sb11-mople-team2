package com.codeit.mople.global.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class KafkaEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public void publish(String topic, Object event) {
    try {
      kafkaTemplate.send(topic, event)
          .whenComplete((result, ex) -> {
            if (ex != null) {
              log.error("Kafka 이벤트 발행 최종 실패: topic={}", topic, ex);
            }
          });
    } catch (Exception e) {
      log.error("Kafka 이벤트 발행 시도 실패: topic={}", topic, e);
    }
  }

  public void publish(String topic, String key, Object event) {
    try {
      kafkaTemplate.send(topic, key, event)
          .whenComplete((result, ex) -> {
            if (ex != null) {
              log.error("Kafka 이벤트 발행 최종 실패: topic={}", topic, ex);
            }
          });
    } catch (Exception e) {
      log.error("Kafka 이벤트 발행 시도 실패: topic={}", topic, e);
    }
  }
}
