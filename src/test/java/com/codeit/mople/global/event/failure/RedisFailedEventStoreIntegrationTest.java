package com.codeit.mople.global.event.failure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@DisplayName("RedisFailedEventStore 통합 테스트")
class RedisFailedEventStoreIntegrationTest {

  static final String NAMESPACE = "mople:test";
  static final String STREAM_KEY = NAMESPACE + ":kafka:events:failed";
  static final String TOPIC = "mople.follow.created.v1";
  static final String EVENT_TYPE = "com.codeit.mople.domain.follow.event.FollowCreatedEvent";
  static final String DATA = "{\"followId\":\"test\"}";
  static final long MAX_ENTRIES = 1_000L;

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  static LettuceConnectionFactory connectionFactory;

  StringRedisTemplate redisTemplate;
  RedisFailedEventStore store;

  @BeforeAll
  static void startConnectionFactory() {
    connectionFactory = new LettuceConnectionFactory(
        new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
    connectionFactory.afterPropertiesSet();
  }

  @AfterAll
  static void stopConnectionFactory() {
    connectionFactory.destroy();
  }

  @BeforeEach
  void setUp() {
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.delete(STREAM_KEY);
    store = new RedisFailedEventStore(redisTemplate, NAMESPACE, MAX_ENTRIES);
  }

  // 앱에는 조회 경로가 없으므로 운영자가 redis-cli 로 보는 것과 같은 방식으로 직접 읽음
  List<MapRecord<String, Object, Object>> readAll() {
    return redisTemplate.opsForStream().range(STREAM_KEY, Range.unbounded());
  }

  FailedEvent failedEvent(String key) {
    return new FailedEvent(TOPIC, key, UUID.randomUUID(), EVENT_TYPE, DATA, "broker down");
  }

  @Test
  @DisplayName("적재한 이벤트가 프로파일 네임스페이스를 붙인 스트림 키에 실제로 쌓이는지")
  void savesToNamespacedStream() {
    // given
    UUID eventId = UUID.randomUUID();

    // when
    store.save(new FailedEvent(TOPIC, "followee-key", eventId, EVENT_TYPE, DATA, "broker down"));

    // then
    assertThat(readAll()).singleElement().satisfies(record ->
        assertThat(record.getValue())
            .containsEntry("topic", TOPIC)
            .containsEntry("key", "followee-key")
            .containsEntry("eventId", eventId.toString())
            .containsEntry("eventType", EVENT_TYPE)
            .containsEntry("data", DATA)
            .containsEntry("error", "broker down"));
  }

  @Test
  @DisplayName("키가 없는 이벤트도 빈 문자열로 쌓여서 적재가 실패하지 않는지")
  void savesEventWithoutKey() {
    // when
    store.save(failedEvent(""));

    // then
    assertThat(readAll()).singleElement().satisfies(record ->
        assertThat(record.getValue()).containsEntry("key", ""));
  }

  @Test
  @DisplayName("여러 건을 적재하면 발생 순서대로 스트림에 쌓이는지")
  void savesInOrder() {
    // when
    store.save(failedEvent("first"));
    store.save(failedEvent("second"));

    // then
    assertThat(readAll()).extracting(record -> record.getValue().get("key"))
        .containsExactly("first", "second");
  }
}