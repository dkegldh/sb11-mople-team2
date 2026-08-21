package com.codeit.mople.global.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class KafkaConfigTest {

  private static final String FAILED_STREAM_KEY = "kafka:events:failed";

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private StreamOperations<String, Object, Object> streamOperations;

  private KafkaConfig kafkaConfig;

  @BeforeEach
  void setUp() {
    kafkaConfig = new KafkaConfig(
        redisTemplate,
        objectMapper,
        new KafkaProperties(true, "test", null)
    );
  }

  @Nested
  @DisplayName("Kafka Consumer 최종 실패 이벤트 저장")
  class SaveFailedEvent {

    @Test
    @DisplayName("최종 실패 이벤트를 Redis Stream에 저장")
    void saveFailedEvent_success() throws Exception {
      // given
      ConsumerRecord<String, Object> record = new ConsumerRecord<>(
          "review-created",
          1,
          100L,
          "content-key",
          new Object()
      );

      RuntimeException exception = new RuntimeException("리뷰 통계 업데이트 실패");

      given(redisTemplate.opsForStream())
          .willReturn(streamOperations);

      given(objectMapper.writeValueAsString(record.value()))
          .willReturn("{\"contentId\":\"test\"}");

      // when
      ReflectionTestUtils.invokeMethod(
          kafkaConfig,
          "saveFailedEvent",
          record,
          exception
      );

      // then
      verify(streamOperations).add(
          eq(FAILED_STREAM_KEY),
          eq(Map.of(
              "type", "CONSUMER",
              "topic", "review-created",
              "key", "content-key",
              "partition", "1",
              "offset", "100",
              "data", "{\"contentId\":\"test\"}",
              "error", "리뷰 통계 업데이트 실패"
          )),
          any(XAddOptions.class)
      );
    }

    @Test
    @DisplayName("최종 실패 이벤트의 key가 null이면 빈 문자열로 저장")
    void saveFailedEvent_nullKey() throws Exception {
      // given
      ConsumerRecord<String, Object> record = new ConsumerRecord<>(
          "review-created",
          0,
          10L,
          null,
          new Object()
      );

      RuntimeException exception =
          new RuntimeException("처리 실패");

      given(redisTemplate.opsForStream())
          .willReturn(streamOperations);

      given(objectMapper.writeValueAsString(record.value()))
          .willReturn("{\"test\":\"data\"}");

      // when
      ReflectionTestUtils.invokeMethod(
          kafkaConfig,
          "saveFailedEvent",
          record,
          exception
      );

      // then
      verify(streamOperations).add(
          eq(FAILED_STREAM_KEY),
          eq(Map.of(
              "type", "CONSUMER",
              "topic", "review-created",
              "key", "",
              "partition", "0",
              "offset", "10",
              "data", "{\"test\":\"data\"}",
              "error", "처리 실패"
          )),
          any(XAddOptions.class)
      );
    }
  }

}
