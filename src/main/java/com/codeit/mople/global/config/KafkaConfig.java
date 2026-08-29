package com.codeit.mople.global.config;

import com.codeit.mople.domain.directmessage.exception.DirectMessageException;
import com.codeit.mople.domain.notification.exception.NotificationException;
import com.codeit.mople.global.event.failure.ConsumeFailureMetricsListener;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.LoggingProducerListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = KafkaProperties.PREFIX, name = "enabled", havingValue = "true")
public class KafkaConfig {

  private static final String DLT_SUFFIX = ".dlt";
  private static final int ANY_PARTITION = -1;

  static final int FAILURE_QUEUE_CAPACITY = 1000;

  static final BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> DLT_DESTINATION_RESOLVER =
      (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, ANY_PARTITION);

  // 큐 포화·종료 이후에도 실패 기록을 버리지 않고 호출 스레드에서 처리
  static final RejectedExecutionHandler CALLER_RUNS_ALWAYS = (task, executor) -> {
    log.warn("Kafka 발행 실패 처리 큐 포화 호출 스레드에서 동기 처리: queueSize={}, shutdown={}",
        executor.getQueue().size(), executor.isShutdown());
    task.run();
  };

  public KafkaConfig(KafkaProperties kafkaProperties) {
    Assert.state(StringUtils.hasText(kafkaProperties.bootstrapServers()),
        "spring.kafka.enabled=true 이지만 spring.kafka.bootstrap-servers 가 비어있습니다. KAFKA_BOOTSTRAP_SERVERS 를 설정하세요.");
    log.info("Kafka 이벤트 발행 활성화: bootstrapServers={}", kafkaProperties.bootstrapServers());
  }

  // 일반 이벤트를 JSON으로 직렬화해서 브로커로 보내는 템플릿
  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate(
      ProducerFactory<String, Object> producerFactory
  ) {
    KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);

    kafkaTemplate.setProducerListener(new LoggingProducerListener<>());

    return kafkaTemplate;
  }

  // send 실패에 대한 후처리를 비동기로
  @Bean
  public ThreadPoolTaskExecutor kafkaPublishFailureExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    executor.setCorePoolSize(1);  // 스레드 1개 고정
    executor.setMaxPoolSize(1);   // 스레드 1개 고정
    executor.setQueueCapacity(FAILURE_QUEUE_CAPACITY);  // 대기 큐 상한
    executor.setThreadNamePrefix("kafka-publish-failure-"); // 로그 스레드 덤프에서 구분용
    executor.setWaitForTasksToCompleteOnShutdown(true);     // 종료시 큐에 남은 작업 버리지 않음
    executor.setAwaitTerminationSeconds(10);                // 버리지 않는대신 최대 10초 기다림
    // 큐가 꽉 차거나 종류 후 들어온 작업은 호출 스레드에서 즉시 실행(유실 방지)
    executor.setRejectedExecutionHandler(CALLER_RUNS_ALWAYS);
    executor.initialize();

    return executor;
  }

  // 역직렬화가 깨진 레코드의 원본 byte[]를 그대로 DLT로 보내는 템플릿
  // JSON 템플릿으로 보내면 Jackson이 base64 문자열로 한 겹 더 감싸버리기 때문임
  @Bean
  @SuppressWarnings("unchecked")
  public KafkaTemplate<String, byte[]> bytesKafkaTemplate(ProducerFactory<?, ?> producerFactory) {
    return new KafkaTemplate<>(
        (ProducerFactory<String, byte[]>) producerFactory,
        Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)
    );
  }

  @Bean
  public DefaultErrorHandler kafkaErrorHandler(
      KafkaTemplate<String, Object> kafkaTemplate,
      KafkaTemplate<String, byte[]> bytesKafkaTemplate,
      ConsumeFailureMetricsListener consumeFailureMetricsListener
  ) {

    // DeadLetterPublishingRecoverer -> 실패 전용 토픽에 던져넣는 기능(DLT로 메세지 보내줌)
    // Consumer가 실패했을 때 그 메세지를 (원래토픽명 + .dlt)로 재발행
    DeadLetterPublishingRecoverer recoverer = new GroupAwareDeadLetterPublishingRecoverer(
        dltTemplates(kafkaTemplate, bytesKafkaTemplate), DLT_DESTINATION_RESOLVER);

    // Exponential : 지수
    // 지수백오프 구현을 위한 객체 생성
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);

    backOff.setInitialInterval(1000L); // 초기 재시도 대기 시간(1초)
    backOff.setMultiplier(2.0); // 다음 재시도에 곱해지는 시간(2배)
    backOff.setMaxInterval(4000L); // 최대 재시도 대기 시간(4초)

    // 재시도 recoverer 이것을 재시도
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

    // SSE 관련 도메인 객체(DM, 알림) 예외 발생 시 재시도 하지 않음(not found 에러 등)
    errorHandler.addNotRetryableExceptions(
        DirectMessageException.class,
        NotificationException.class
    );

    errorHandler.setRetryListeners(consumeFailureMetricsListener);

    return errorHandler;
  }

  // 값 타입을 findFirst로 고르기 때문에 byte[]를 Object보다 앞에 넣어두는 맵
  static Map<Class<?>, KafkaOperations<?, ?>> dltTemplates(
      KafkaTemplate<String, Object> kafkaTemplate,
      KafkaTemplate<String, byte[]> bytesKafkaTemplate
  ) {
    Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();

    templates.put(byte[].class, bytesKafkaTemplate);
    templates.put(Object.class, kafkaTemplate);

    return templates;
  }

  // GroupId를 헤더에 추가하는 커스텀 DLT 리커버러
  static class GroupAwareDeadLetterPublishingRecoverer extends DeadLetterPublishingRecoverer {

    public GroupAwareDeadLetterPublishingRecoverer(
        Map<Class<?>, KafkaOperations<?, ?>> templates,
        BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver) {
      super(templates, destinationResolver);
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, org.apache.kafka.clients.consumer.Consumer<?, ?> consumer, Exception exception) {
      String groupId = "UNKNOWN";

      if (consumer != null) {
        try {
          groupId = consumer.groupMetadata().groupId();
        } catch (Exception e) {
          log.warn("Kafka Consumer 그룹 ID 추출 실패 - 기본값(UNKNOWN)으로 세팅", e);
        }
      }

      // DLT로 가기 전 헤더에 안전하게 주입
      record.headers().add("x-original-group-id", groupId.getBytes(StandardCharsets.UTF_8));

      super.accept(record, consumer, exception);
    }
  }
}