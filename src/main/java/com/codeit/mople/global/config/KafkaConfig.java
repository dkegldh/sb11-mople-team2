package com.codeit.mople.global.config;

import com.codeit.mople.domain.directmessage.exception.DirectMessageException;
import com.codeit.mople.domain.notification.exception.NotificationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {

  private static final String FAILED_STREAM_KEY = "kafka:events:failed";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public KafkaConfig(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      @Value("${spring.kafka.bootstrap-servers:}") String bootstrapServers
  ) {
    Assert.state(StringUtils.hasText(bootstrapServers),
        "kafka.enabled=true 이지만 spring.kafka.bootstrap-servers 가 비어있습니다. KAFKA_BOOTSTRAP_SERVERS 를 설정하세요.");
    log.info("Kafka 이벤트 발행 활성화: bootstrapServers={}", bootstrapServers);

    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  @Bean
  public DefaultErrorHandler kafkaErrorHandler() {
    ConsumerRecordRecoverer recoverer = this::saveFailedEvent;

    // Exponential : 지수
    // 지수백오프 구현을 위한 객체 생성
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);

    backOff.setInitialInterval(1000L); // 초기 재시도 대기 시간(1초)
    backOff.setMultiplier(2.0); // 다음 재시도에 곱해지는 시간(2배)
    backOff.setMaxInterval(4000L); // 최대 재시도 대기 시간(4초)

    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

    // SSE 관련 도메인 객체(DM, 알림) 예외 발생 시 재시도 하지 않음(not found 에러 등)
    errorHandler.addNotRetryableExceptions(
        DirectMessageException.class,
        NotificationException.class
    );

    return new DefaultErrorHandler(recoverer, backOff);
  }

  private void saveFailedEvent(
      ConsumerRecord<?, ?> record,
      Exception ex
  ) {
    try {
      String data = objectMapper.writeValueAsString(record.value());

      // record(Consumer)는 partition, offest 등도 같이 제공해줌
      // StringRedisTemplate을 통해 Redis Stream에 넣어줌
      redisTemplate.opsForStream().add(
          FAILED_STREAM_KEY,
          Map.of(
              "type", "CONSUMER",
              "topic", record.topic(),
              "key", record.key() == null ? "" : record.key().toString(),
              "data", data,
              "partition", String.valueOf(record.partition()),
              "offset", String.valueOf(record.offset()),
              "error", ex == null ? "Unknown" : String.valueOf(ex.getMessage())
          )
      );

      log.error("Kafka Consumer 이벤트 최종 실패: topic={}, partition={}, offset={}, key{}",
          record.topic(), record.partition(), record.offset(), record.key(), ex);
    } catch (JsonProcessingException e) {
      log.error("Kafka Consumer 최종 실패 이벤트 직렬화 실패: topic={}, key={}",
          record.topic(), record.key(), e);

      throw new IllegalStateException("Kafka 최종 실패 이벤트 직렬화 실패", e);
    } catch (Exception e) {
      log.error("Kafka Consumer 최종 실패 이벤트 Redis 저장 실패: topic={}, partition={}, offset={}, key={}",
          record.topic(), record.partition(), record.offset(), record.key(), e);

      throw new IllegalStateException("Kafka 최종 실패 이벤트 Redis 저장 실패", e);
    }
  }

}
