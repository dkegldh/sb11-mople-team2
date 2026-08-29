package com.codeit.mople.global.event;

import com.codeit.mople.global.config.KafkaProperties;
import com.codeit.mople.global.event.failure.FailedEvent;
import com.codeit.mople.global.event.failure.FailedEventStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = KafkaProperties.PREFIX, name = "enabled", havingValue = "true")
public class KafkaEventPublisher {

  private static final String FAILURE_COUNTER = "kafka.event.publish.failure";
  private static final String DESCRIPTION = "발행 최종 실패로 Redis 에 적재된 이벤트 수";

  private static final String TAG_TOPIC = "topic";
  private static final String TAG_REASON = "reason";
  private static final String UNKNOWN_REASON = "Unknown";

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final FailedEventStore failedEventStore;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final Executor failureExecutor;

  public KafkaEventPublisher(
      KafkaTemplate<String, Object> kafkaTemplate,
      FailedEventStore failedEventStore,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @Qualifier("kafkaPublishFailureExecutor") Executor failureExecutor
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.failedEventStore = failedEventStore;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.failureExecutor = failureExecutor;
  }

  public void publish(String topic, PublishableEvent event) {
    publish(topic, null, event);
  }

  public void publish(String topic, String key, PublishableEvent event) {
    send(topic, key, event).whenComplete((result, cause) -> {
      if (cause != null) {
        // 실패를 하나의 객체로 생성
        failureExecutor.execute(() -> handleFailure(topic, key, event, cause));
      }
    });
  }

  // 브로커에 발행 시도
  // CompletableFuture: 이것을 사용하여 미리 결과를 기다리지 않고 즉시 리턴, 리턴값은 비동기로 채워짐
  private CompletableFuture<SendResult<String, Object>> send(
      String topic,
      String key,
      PublishableEvent event
  ) {
    try {
      return kafkaTemplate.send(topic, key, event);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  // 프로듀서 최종 실패를 하나의 객채로 생성 및 로그 기록(여기서 만들어진 객체는 redis에 저장할 객체임)
  private void handleFailure(String topic, String key, PublishableEvent event, Throwable cause) {
    failedEventStore.save(FailedEvent.of(topic, key, event, serialize(topic, key, event), cause));
    countFailure(topic, cause);

    log.error("{}: topic={}, key={}, eventId={}, eventType={}",
        reasonOf(cause), topic, key, event.eventId(), event.getClass().getSimpleName(), cause);
  }

  // 예외 메시지를 태그로 쓰면 값이 무한히 늘어나므로 클래스 이름만 쓰는 집계
  private void countFailure(String topic, Throwable cause) {
    Counter.builder(FAILURE_COUNTER)
        .description(DESCRIPTION)
        .tag(TAG_TOPIC, topic)
        .tag(TAG_REASON, reasonTagOf(cause))
        .register(meterRegistry)
        .increment();
  }

  // 소비 쪽 ConsumeFailureMetricsListener 와 같은 규칙으로 원인 태그를 뽑음
  private String reasonTagOf(Throwable cause) {
    if (cause == null) {
      return UNKNOWN_REASON;
    }

    return NestedExceptionUtils.getMostSpecificCause(cause).getClass().getSimpleName();
  }

  // cause가 serializationException타입이면 "Kafka 이벤트 직렬화 실패" 아니면 "Kafka 이벤트 발행 최종 실패"
  private String reasonOf(Throwable cause) {
    return cause instanceof SerializationException
        ? "Kafka 이벤트 직렬화 실패"
        : "Kafka 이벤트 발행 최종 실패";
  }

  // event를 JSON 문자열로 직렬화 Redis Stream은 문자열 값만 저장 가능해서 변환 필요
  // 변환 도중 문제가 생기면 로그 기록
  private String serialize(String topic, String key, PublishableEvent event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      log.error("Kafka Producer 실패 이벤트 본문 직렬화 실패: topic={}, key={}, eventId={}, eventType={}",
          topic, key, event.eventId(), event.getClass().getSimpleName(), e);
      return "";
    }
  }
}