package com.codeit.mople.global.event.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisFailedEventStore 테스트")
class RedisFailedEventStoreTest {

  static final String NAMESPACE = "mople:test";
  static final String STREAM_KEY = "mople:test:kafka:events:failed";
  static final int MAX_ENTRIES = 1_000;
  static final String EVENT_TYPE = "com.codeit.mople.domain.follow.event.FollowCreatedEvent";
  static final Duration WITHIN = Duration.ofHours(24);

  @Mock
  StringRedisTemplate redisTemplate;
  @Mock
  StreamOperations<String, Object, Object> streamOperations;

  @Captor
  ArgumentCaptor<XAddOptions> optionsCaptor;
  @Captor
  ArgumentCaptor<Map<String, String>> fieldsCaptor;
  @Captor
  ArgumentCaptor<Range<String>> rangeCaptor;
  @Captor
  ArgumentCaptor<Limit> limitCaptor;

  RedisFailedEventStore store;

  UUID eventId;
  FailedEvent event;

  @BeforeEach
  void setUp() {
    store = new RedisFailedEventStore(redisTemplate, NAMESPACE, MAX_ENTRIES);
    eventId = UUID.randomUUID();
    event = new FailedEvent(
        "mople.follow.created.v1",
        "followee-key",
        eventId,
        EVENT_TYPE,
        "{\"followId\":\"test\"}",
        "broker down"
    );
  }

  @Nested
  @DisplayName("발행 실패 이벤트 적재")
  class Save {

    @Test
    @DisplayName("프로파일 네임스페이스를 붙인 스트림 키에 이벤트가 제대로 쌓이는지")
    void saveSuccess() {
      // given
      given(redisTemplate.opsForStream()).willReturn(streamOperations);

      // when
      store.save(event);

      // then
      verify(streamOperations).add(
          eq(STREAM_KEY),
          eq(Map.of(
              "topic", "mople.follow.created.v1",
              "key", "followee-key",
              "eventId", eventId.toString(),
              "eventType", EVENT_TYPE,
              "data", "{\"followId\":\"test\"}",
              "error", "broker down"
          )),
          any(XAddOptions.class)
      );
    }

    @Test
    @DisplayName("적재할 때마다 근사 크기 상한 옵션이 같이 넘어가는지")
    void saveWithSizeLimitOption() {
      // given
      given(redisTemplate.opsForStream()).willReturn(streamOperations);

      // when
      store.save(event);

      // then
      verify(streamOperations).add(eq(STREAM_KEY), anyMap(), optionsCaptor.capture());

      XAddOptions options = optionsCaptor.getValue();
      assertThat(options.hasMaxlen()).isTrue();
      assertThat(options.getMaxlen()).isEqualTo(MAX_ENTRIES);
      assertThat(options.isApproximateTrimming()).isTrue();
      assertThat(options.hasMinId()).isFalse();
    }

    @Test
    @DisplayName("키가 없는 이벤트를 집어 넣으면 빈 문자열로 쌓이는지")
    void saveWhenKeyIsEmpty() {
      // given
      given(redisTemplate.opsForStream()).willReturn(streamOperations);
      FailedEvent noKey = new FailedEvent(
        "mople.notification.created.v1",
          "",
          eventId,
          EVENT_TYPE,
          "{}",
          "broker down"
      );

      // when
      store.save(noKey);

      // then
      verify(streamOperations).add(eq(STREAM_KEY), fieldsCaptor.capture(), any(XAddOptions.class));

      assertThat(fieldsCaptor.getValue()).containsEntry("key", "");
    }

    @Test
    @DisplayName("Redis가 죽어 있어도 예외를 밖으로 던지지 않는지")
    void saveWhenRedisDown() {
      // given
      given(redisTemplate.opsForStream())
          .willThrow(new RedisConnectionFailureException("redis down"));

      // when & then
      assertThatCode(() -> store.save(event)).doesNotThrowAnyException();
    }
  }

}
