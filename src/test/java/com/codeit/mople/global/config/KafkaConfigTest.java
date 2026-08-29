package com.codeit.mople.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaConfig 테스트")
class KafkaConfigTest {

  static final String TOPIC = "mople.follow.created.v1";

  ConsumerRecord<String, Object> record;
  RuntimeException exception;

  @BeforeEach
  void setUp() {
    record = new ConsumerRecord<>(TOPIC, 2, 100L, "followee-key", new Object());
    exception = new RuntimeException("알림 생성 실패");
  }

  @Nested
  @DisplayName("DLT 목적지 해석")
  class DeadLetterDestination {

    @Test
    @DisplayName("원 토픽 이름 뒤에 .dlt를 붙인 토픽으로 보내는지")
    void resolveDltTopic() {
      // when
      TopicPartition destination =
          KafkaConfig.DLT_DESTINATION_RESOLVER.apply(record, exception);

      // then
      assertThat(destination.topic()).isEqualTo("mople.follow.created.v1.dlt");
    }

    @Test
    @DisplayName("파티션을 지정하지 않아서 DLT 파티션 수가 더 적어도 발행이 안 깨지는지")
    void resolveAnyPartition() {
      // when
      TopicPartition destination =
          KafkaConfig.DLT_DESTINATION_RESOLVER.apply(record, exception);

      // then
      assertThat(destination.partition()).isEqualTo(-1);
    }
  }

  @Nested
  @DisplayName("발행 실패 처리 executor")
  class FailureExecutor {

    ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
      executor = new KafkaConfig(KafkaConfigContextTest.kafkaProperties())
          .kafkaPublishFailureExecutor();
    }

    @AfterEach
    void tearDown() {
      executor.shutdown();
    }

    @Test
    @DisplayName("큐가 가득 차도 실패 기록이 호출 스레드에서 실행되는지")
    void runsOnCallerThreadWhenSaturated() {
      // given
      CountDownLatch blocked = new CountDownLatch(1);
      executor.execute(() -> await(blocked));
      for (int i = 0; i < KafkaConfig.FAILURE_QUEUE_CAPACITY; i++) {
        executor.execute(() -> await(blocked));
      }

      // when
      AtomicReference<String> ranOn = new AtomicReference<>();
      executor.execute(() -> ranOn.set(Thread.currentThread().getName()));

      // then
      assertThat(ranOn.get()).isEqualTo(Thread.currentThread().getName());
      blocked.countDown();
    }

    @Test
    @DisplayName("executor 가 이미 종료됐어도 실패 기록이 실행되는지")
    void runsAfterShutdown() {
      // given
      executor.shutdown();

      // when
      AtomicBoolean ran = new AtomicBoolean();
      executor.execute(() -> ran.set(true));

      // then
      assertThat(ran).isTrue();
    }

    void await(CountDownLatch latch) {
      try {
        latch.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Nested
  @DisplayName("DLT 발행 템플릿 선택")
  class DltTemplates {

    @Mock
    KafkaTemplate<String, Object> jsonTemplate;
    @Mock
    KafkaTemplate<String, byte[]> bytesTemplate;

    @Test
    @DisplayName("byte[] 가 Object 보다 먼저 와야 역직렬화 실패분이 바이트 템플릿으로 가는지")
    void bytesTemplateComesFirst() {
      // when
      Map<Class<?>, KafkaOperations<?, ?>> templates =
          KafkaConfig.dltTemplates(jsonTemplate, bytesTemplate);

      // then
      assertThat(templates.keySet()).containsExactly(byte[].class, Object.class);
      assertThat(templates.get(byte[].class)).isSameAs(bytesTemplate);
      assertThat(templates.get(Object.class)).isSameAs(jsonTemplate);
    }
  }

  @Nested
  @DisplayName("GroupAware 커스텀 리커버러 테스트")
  class GroupAwareRecovererTest {

    @Mock
    KafkaOperations<Object, Object> template;

    @Mock
    Consumer<?, ?> consumer;

    @Mock
    ConsumerGroupMetadata groupMetadata;

    @Test
    @DisplayName("컨슈머 그룹 ID가 정상적으로 추출되어 레코드 헤더에 추가되는지 검증")
    void addGroupIdHeaderSuccessfully() {
      // given
      Map<Class<?>, KafkaOperations<?, ?>> templates = Map.of(Object.class, template);
      KafkaConfig.GroupAwareDeadLetterPublishingRecoverer recoverer =
          new KafkaConfig.GroupAwareDeadLetterPublishingRecoverer(templates, KafkaConfig.DLT_DESTINATION_RESOLVER);

      given(consumer.groupMetadata()).willReturn(groupMetadata);
      given(groupMetadata.groupId()).willReturn("mople-dm-es-sync-group");

      // 부모 클래스(super.accept)가 내부적으로 template.send()를 호출하므로 에러 방지용 Mocking
      given(template.send(any(ProducerRecord.class)))
          .willReturn(CompletableFuture.completedFuture(null));

      // when
      recoverer.accept(record, consumer, exception);

      // then
      byte[] headerValue = record.headers().lastHeader("x-original-group-id").value();
      String extractedGroupId = new String(headerValue, StandardCharsets.UTF_8);

      assertThat(extractedGroupId).isEqualTo("mople-dm-es-sync-group");
    }

    @Test
    @DisplayName("Consumer가 null이거나 예외 발생 시 헤더에 UNKNOWN이 세팅되는지 검증")
    void addUnknownGroupIdHeaderWhenConsumerIsNull() {
      // given
      Map<Class<?>, KafkaOperations<?, ?>> templates = Map.of(Object.class, template);
      KafkaConfig.GroupAwareDeadLetterPublishingRecoverer recoverer =
          new KafkaConfig.GroupAwareDeadLetterPublishingRecoverer(templates, KafkaConfig.DLT_DESTINATION_RESOLVER);

      given(template.send(any(ProducerRecord.class)))
          .willReturn(CompletableFuture.completedFuture(null));

      // when (consumer를 null로 넘김)
      recoverer.accept(record, null, exception);

      // then
      byte[] headerValue = record.headers().lastHeader("x-original-group-id").value();
      String extractedGroupId = new String(headerValue, StandardCharsets.UTF_8);

      assertThat(extractedGroupId).isEqualTo("UNKNOWN");
    }
  }
}