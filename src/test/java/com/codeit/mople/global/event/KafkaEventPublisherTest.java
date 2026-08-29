package com.codeit.mople.global.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeit.mople.global.event.failure.FailedEvent;
import com.codeit.mople.global.event.failure.FailedEventStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventPublisher 테스트")
class KafkaEventPublisherTest {

  static final String TOPIC = "mople.follow.created.v1";
  static final String KEY = "followee-key";

  record TestEvent(UUID eventId, Instant occurredAt) implements PublishableEvent {}

  @Mock
  KafkaTemplate<String, Object> kafkaTemplate;
  @Mock
  FailedEventStore failedEventStore;
  @Mock
  ObjectMapper objectMapper;

  @Captor
  ArgumentCaptor<FailedEvent> failedEventCaptor;

  TestEvent event;
  MeterRegistry meterRegistry;
  KafkaEventPublisher publisher;

  final Executor sameThreadExecutor = Runnable::run;
  final Executor neverRunsExecutor = command -> {
  };

  @BeforeEach
  void setUp() {
    event = new TestEvent(UUID.randomUUID(), Instant.now());
    meterRegistry = new SimpleMeterRegistry();
    publisher = new KafkaEventPublisher(
        kafkaTemplate, failedEventStore, objectMapper, meterRegistry, sameThreadExecutor);
  }

  @Nested
  @DisplayName("이벤트 발행")
  class Publish {

    @Test
    @DisplayName("발행에 성공하면 실패 이벤트를 쌓지 않는지")
    void publishSuccess() {
      // given
      CompletableFuture<SendResult<String, Object>> sent =
          CompletableFuture.completedFuture(null);
      given(kafkaTemplate.send(TOPIC, KEY, event)).willReturn(sent);

      // when
      publisher.publish(TOPIC, KEY, event);

      // then
      verify(failedEventStore, never()).save(any());
    }

    @Test
    @DisplayName("발행에 실패하면 토픽과 키와 이벤트 정보를 담아서 잘 쌓는지")
    void publishFailWhenSendFails() throws Exception {
      // given
      CompletableFuture<SendResult<String, Object>> failed =
          CompletableFuture.failedFuture(new RuntimeException("broker down"));
      given(kafkaTemplate.send(TOPIC, KEY, event)).willReturn(failed);
      given(objectMapper.writeValueAsString(event)).willReturn("{\"eventId\":\"test\"}");

      // when
      publisher.publish(TOPIC, KEY, event);

      // then
      verify(failedEventStore).save(failedEventCaptor.capture());

      FailedEvent saved = failedEventCaptor.getValue();
      assertThat(saved.topic()).isEqualTo(TOPIC);
      assertThat(saved.key()).isEqualTo(KEY);
      assertThat(saved.eventId()).isEqualTo(event.eventId());
      assertThat(saved.eventType()).isEqualTo(TestEvent.class.getName());
      assertThat(saved.data()).isEqualTo("{\"eventId\":\"test\"}");
      assertThat(saved.error()).isEqualTo("broker down");
    }

    @Test
    @DisplayName("본문 직렬화가 깨져도 본문만 비운 채로 실패 이벤트를 쌓는지")
    void publishFailWhenSerializeFails() throws Exception {
      // given
      CompletableFuture<SendResult<String, Object>> failed =
          CompletableFuture.failedFuture(new RuntimeException("broker down"));
      given(kafkaTemplate.send(TOPIC, KEY, event)).willReturn(failed);
      given(objectMapper.writeValueAsString(event))
          .willThrow(new JsonProcessingException("직렬화 실패") {});

      // when
      publisher.publish(TOPIC, KEY, event);

      // then
      verify(failedEventStore).save(failedEventCaptor.capture());

      FailedEvent saved = failedEventCaptor.getValue();
      assertThat(saved.data()).isEmpty();
      assertThat(saved.eventId()).isEqualTo(event.eventId());
      assertThat(saved.eventType()).isEqualTo(TestEvent.class.getName());
    }

    @Test
    @DisplayName("발행을 시도하다 예외가 터져도 실패 이벤트를 쌓는지")
    void publishFailWhenSendThrows() throws Exception {
      // given
      given(kafkaTemplate.send(TOPIC, KEY, event))
          .willThrow(new IllegalStateException("max.block.ms 만료"));
      given(objectMapper.writeValueAsString(event)).willReturn("{\"eventId\":\"test\"}");

      // when
      publisher.publish(TOPIC, KEY, event);

      // then
      verify(failedEventStore).save(failedEventCaptor.capture());
      assertThat(failedEventCaptor.getValue().error()).isEqualTo("max.block.ms 만료");
    }

    @Test
    @DisplayName("발행에 성공하면 실패 처리 executor 에 아무것도 제출하지 않는지")
    void publishSuccessSubmitsNothing() {
      // given
      AtomicInteger submitted = new AtomicInteger();
      KafkaEventPublisher counting = new KafkaEventPublisher(
          kafkaTemplate, failedEventStore, objectMapper, meterRegistry,
          command -> {
            submitted.incrementAndGet();
            command.run();
          });
      given(kafkaTemplate.send(TOPIC, KEY, event))
          .willReturn(CompletableFuture.completedFuture(null));

      // when
      counting.publish(TOPIC, KEY, event);

      // then
      assertThat(submitted).hasValue(0);
    }

    @Test
    @DisplayName("발행에 실패하면 실패 처리를 executor 에 한 번만 제출하는지")
    void publishFailureSubmitsOnce() throws Exception {
      // given
      AtomicInteger submitted = new AtomicInteger();
      KafkaEventPublisher counting = new KafkaEventPublisher(
          kafkaTemplate, failedEventStore, objectMapper, meterRegistry,
          command -> {
            submitted.incrementAndGet();
            command.run();
          });
      given(kafkaTemplate.send(TOPIC, KEY, event))
          .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
      given(objectMapper.writeValueAsString(event)).willReturn("{}");

      // when
      counting.publish(TOPIC, KEY, event);

      // then
      assertThat(submitted).hasValue(1);
      verify(failedEventStore).save(any());
    }

    @Test
    @DisplayName("발행 실패 처리가 호출 스레드가 아니라 전용 executor를 거쳐서 도는지")
    void handlesFailureThroughExecutor() {
      // given
      KafkaEventPublisher deferred = new KafkaEventPublisher(
          kafkaTemplate, failedEventStore, objectMapper, meterRegistry, neverRunsExecutor);
      given(kafkaTemplate.send(TOPIC, KEY, event))
          .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

      // when
      deferred.publish(TOPIC, KEY, event);

      // then
      verify(failedEventStore, never()).save(any());
      assertThat(meterRegistry.find("kafka.event.publish.failure").counter()).isNull();
    }

    @Test
    @DisplayName("키 없이 발행하면 키가 null로 넘어가는지")
    void publishWithoutKey() {
      // given
      CompletableFuture<SendResult<String, Object>> sent =
          CompletableFuture.completedFuture(null);
      given(kafkaTemplate.send(TOPIC, (String) null, event)).willReturn(sent);

      // when
      publisher.publish(TOPIC, event);

      // then
      verify(kafkaTemplate).send(TOPIC, (String) null, event);
      verify(failedEventStore, never()).save(any());
    }
  }

  @Nested
  @DisplayName("발행 실패 집계")
  class FailureMetrics {

    @Test
    @DisplayName("발행에 실패하면 토픽과 실패 원인을 태그로 달아 세는지")
    void countsFailureByTopicAndReason() throws Exception {
      // given
      given(kafkaTemplate.send(TOPIC, KEY, event))
          .willReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
      given(objectMapper.writeValueAsString(event)).willReturn("{}");

      // when
      publisher.publish(TOPIC, KEY, event);

      // then
      assertThat(meterRegistry.get("kafka.event.publish.failure")
          .tag("topic", TOPIC)
          .tag("reason", "IllegalStateException")
          .counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("발행에 성공하면 아무것도 세지 않는지")
    void countsNothingOnSuccess() {
      // given
      given(kafkaTemplate.send(TOPIC, KEY, event))
          .willReturn(CompletableFuture.completedFuture(null));

      // when
      publisher.publish(TOPIC, KEY, event);

      // then
      assertThat(meterRegistry.find("kafka.event.publish.failure").counter()).isNull();
    }
  }
}