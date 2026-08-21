package com.codeit.mople.global.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.follow.event.FollowCreatedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistContentAddedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.kafka.enabled=true",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.producer.properties.max.block.ms=10000",
    "spring.kafka.listener.auto-startup=false"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {
    KafkaRelaySmokeTest.FOLLOW_TOPIC,
    KafkaRelaySmokeTest.PLAYLIST_CONTENT_TOPIC
})
@DisplayName("Kafka 릴레이 스모크 테스트")
class KafkaRelaySmokeTest {

  static final String FOLLOW_TOPIC = "mople.follow.created.v1";
  static final String PLAYLIST_CONTENT_TOPIC = "mople.playlist.content-added.v1";

  @Autowired
  ApplicationEventPublisher eventPublisher;

  @Autowired
  TransactionTemplate transactionTemplate;

  @Autowired
  EmbeddedKafkaBroker embeddedKafka;
  
  private Consumer<String, String> consumerFor(String topic) {
    Map<String, Object> props =
        KafkaTestUtils.consumerProps("smoke-" + UUID.randomUUID(), "true", embeddedKafka);
    Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
        props, new StringDeserializer(), new StringDeserializer()).createConsumer();
    embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);

    return consumer;
  }

  @Nested
  @DisplayName("커밋 이후 발행")
  class PublishAfterCommit {

    @Test
    @DisplayName("팔로우 생성 이벤트가 커밋된 뒤 토픽에 1건 나가는지")
    void publishesFollowCreated() {
      // given
      UUID followId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      UUID followerId = UUID.randomUUID();

      try (Consumer<String, String> consumer = consumerFor(FOLLOW_TOPIC)) {
        // when
        transactionTemplate.executeWithoutResult(status ->
            eventPublisher.publishEvent(
                new FollowCreatedEvent(
                    UUID.randomUUID(), Instant.now(), followId, followeeId, followerId, "아메리카노좋아")));

        // then
        ConsumerRecord<String, String> record =
            KafkaTestUtils.getSingleRecord(consumer, FOLLOW_TOPIC, Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo(followeeId.toString());
        assertThat(record.value()).contains(
            followId.toString(), followeeId.toString(), followerId.toString(), "아메리카노좋아");
      }
    }

    @Test
    @DisplayName("콘텐츠 추가 이벤트가 커밋된 뒤 토픽에 1건 나가는지")
    void publishesPlaylistContentAdded() {
      // given
      UUID playlistContentId = UUID.randomUUID();
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();

      try (Consumer<String, String> consumer = consumerFor(PLAYLIST_CONTENT_TOPIC)) {
        // when
        transactionTemplate.executeWithoutResult(status ->
            eventPublisher.publishEvent(
                new PlaylistContentAddedEvent(
                    UUID.randomUUID(), Instant.now(), playlistContentId, playlistId, contentId, "테스트 플레이리스트")));

        // then
        ConsumerRecord<String, String> record =
            KafkaTestUtils.getSingleRecord(consumer, PLAYLIST_CONTENT_TOPIC, Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo(playlistId.toString());
        assertThat(record.value()).contains(
            playlistContentId.toString(), playlistId.toString(), contentId.toString(), "테스트 플레이리스트");
      }
    }
  }
}