package com.codeit.mople.global.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.event.ContentSearchIndexDeleteEvent;
import com.codeit.mople.domain.content.event.ContentSearchIndexEvent;
import com.codeit.mople.domain.directmessage.event.DirectMessageCreatedEvent;
import com.codeit.mople.domain.follow.event.FollowCreatedEvent;
import com.codeit.mople.domain.notification.event.NotificationCreatedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistSearchIndexDeleteEvent;
import com.codeit.mople.domain.playlist.event.PlaylistSearchIndexEvent;
import com.codeit.mople.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistUnsubscribedEvent;
import com.codeit.mople.domain.review.event.ReviewCreatedEvent;
import com.codeit.mople.domain.review.event.ReviewDeletedEvent;
import com.codeit.mople.domain.review.event.ReviewUpdatedEvent;
import com.codeit.mople.domain.user.event.UserSearchIndexEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    "spring.kafka.listener.auto-startup=false"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {
    KafkaPublishSmokeTest.FOLLOW_TOPIC,
    KafkaPublishSmokeTest.PLAYLIST_CONTENT_TOPIC,
    KafkaPublishSmokeTest.PLAYLIST_EVENTS_TOPIC,
    KafkaPublishSmokeTest.PLAYLIST_SEARCH_INDEX_TOPIC,
    KafkaPublishSmokeTest.CONTENT_SEARCH_INDEX_TOPIC,
    KafkaPublishSmokeTest.USER_SEARCH_INDEX_TOPIC,
    KafkaPublishSmokeTest.DIRECT_MESSAGE_TOPIC,
    KafkaPublishSmokeTest.NOTIFICATION_TOPIC,
    KafkaPublishSmokeTest.REVIEW_CREATED_TOPIC,
    KafkaPublishSmokeTest.REVIEW_UPDATED_TOPIC,
    KafkaPublishSmokeTest.REVIEW_DELETED_TOPIC
})
@DisplayName("Kafka 발행 스모크 테스트")
class KafkaPublishSmokeTest {

  static final String FOLLOW_TOPIC = "mople.follow.created.v1";
  static final String PLAYLIST_CONTENT_TOPIC = "mople.playlist.content-added.v1";
  static final String PLAYLIST_EVENTS_TOPIC = "mople.playlist-events.v1";
  static final String PLAYLIST_SEARCH_INDEX_TOPIC = "mople.playlist-search-index-events.v1";
  static final String CONTENT_SEARCH_INDEX_TOPIC = "mople.content-search-index-events.v1";
  static final String USER_SEARCH_INDEX_TOPIC = "mople.user-search-index-events.v1";
  static final String DIRECT_MESSAGE_TOPIC = "mople.direct-message.created.v1";
  static final String NOTIFICATION_TOPIC = "mople.notification.created.v1";
  static final String REVIEW_CREATED_TOPIC = "mople.review.created.v1";
  static final String REVIEW_UPDATED_TOPIC = "mople.review.updated.v1";
  static final String REVIEW_DELETED_TOPIC = "mople.review.deleted.v1";

  static final String TYPE_ID_HEADER = "__TypeId__";

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
    embeddedKafka.consumeFromAnEmbeddedTopic(consumer, true, topic);

    return consumer;
  }

  private String typeIdOf(ConsumerRecord<String, String> record) {
    Header header = record.headers().lastHeader(TYPE_ID_HEADER);

    assertThat(header)
        .as("%s 헤더가 안 실림", TYPE_ID_HEADER)
        .isNotNull();

    return new String(header.value(), StandardCharsets.UTF_8);
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

  @Nested
  @DisplayName("타입 헤더")
  class TypeHeader {

    @Test
    @DisplayName("팔로우 생성 이벤트 헤더에 follow-created.v1이 실리는지")
    void carriesAliasForFollowCreated() {
      // given
      UUID followeeId = UUID.randomUUID();

      try (Consumer<String, String> consumer = consumerFor(FOLLOW_TOPIC)) {
        // when
        transactionTemplate.executeWithoutResult(status ->
            eventPublisher.publishEvent(
                new FollowCreatedEvent(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), followeeId,
                    UUID.randomUUID(), "아메리카노좋아")));

        // then
        ConsumerRecord<String, String> record =
            KafkaTestUtils.getSingleRecord(consumer, FOLLOW_TOPIC, Duration.ofSeconds(10));

        assertThat(typeIdOf(record)).isEqualTo("follow-created.v1");
      }
    }

    @Test
    @DisplayName("콘텐츠 추가 이벤트 헤더에 playlist-content-added.v1이 실리는지")
    void carriesAliasForPlaylistContentAdded() {
      // given
      UUID playlistId = UUID.randomUUID();

      try (Consumer<String, String> consumer = consumerFor(PLAYLIST_CONTENT_TOPIC)) {
        // when
        transactionTemplate.executeWithoutResult(status ->
            eventPublisher.publishEvent(
                new PlaylistContentAddedEvent(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), playlistId,
                    UUID.randomUUID(), "테스트 플레이리스트")));

        // then
        ConsumerRecord<String, String> record =
            KafkaTestUtils.getSingleRecord(consumer, PLAYLIST_CONTENT_TOPIC, Duration.ofSeconds(10));

        assertThat(typeIdOf(record)).isEqualTo("playlist-content-added.v1");
      }
    }

  }

  @Nested
  @DisplayName("전체 발행 이벤트")
  class EveryPublishedEvent {

    static Stream<Arguments> events() {
      UUID id = UUID.randomUUID();

      return Stream.of(
          arguments(new PlaylistSubscribedEvent(UUID.randomUUID(), Instant.now(), id, id, id, "구독자", "플리"),
              PLAYLIST_EVENTS_TOPIC, "playlist-subscribed.v1"),
          arguments(new PlaylistUnsubscribedEvent(UUID.randomUUID(), Instant.now(), id, id),
              PLAYLIST_EVENTS_TOPIC, "playlist-unsubscribed.v1"),
          arguments(new PlaylistSearchIndexEvent(UUID.randomUUID(), Instant.now(), id, "플리", Instant.now(), 3L),
              PLAYLIST_SEARCH_INDEX_TOPIC, "playlist-search-index.v1"),
          arguments(new PlaylistSearchIndexDeleteEvent(UUID.randomUUID(), Instant.now(), id),
              PLAYLIST_SEARCH_INDEX_TOPIC, "playlist-search-index-delete.v1"),
          arguments(new ContentSearchIndexEvent(UUID.randomUUID(), Instant.now(), id, "콘텐츠", ContentType.MOVIE, 4.5, 10L, Instant.now()),
              CONTENT_SEARCH_INDEX_TOPIC, "content-search-index.v1"),
          arguments(new ContentSearchIndexDeleteEvent(UUID.randomUUID(), Instant.now(), id),
              CONTENT_SEARCH_INDEX_TOPIC, "content-search-index-delete.v1"),
          arguments(new UserSearchIndexEvent(UUID.randomUUID(), Instant.now(), id, "a@mople.com", "이름", Instant.now(), false, "USER"),
              USER_SEARCH_INDEX_TOPIC, "user-search-index.v1"),
          arguments(new ReviewCreatedEvent(UUID.randomUUID(), Instant.now(), id, 4.5),
              REVIEW_CREATED_TOPIC, "review-created.v1"),
          arguments(new ReviewUpdatedEvent(UUID.randomUUID(), Instant.now(), id, 3.0, 4.5),
              REVIEW_UPDATED_TOPIC, "review-updated.v1"),
          arguments(new ReviewDeletedEvent(UUID.randomUUID(), Instant.now(), id, 4.5),
              REVIEW_DELETED_TOPIC, "review-deleted.v1"),
          arguments(new DirectMessageCreatedEvent(UUID.randomUUID(), Instant.now(), id, id),
              DIRECT_MESSAGE_TOPIC, "direct-message-created.v1"),
          arguments(new NotificationCreatedEvent(UUID.randomUUID(), Instant.now(), id, id),
              NOTIFICATION_TOPIC, "notification-created.v1")
      );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("events")
    @DisplayName("커밋 뒤 제 토픽으로 나가고 헤더에 제 타입 이름이 실리는지")
    void publishesWithTypeName(PublishableEvent event, String topic, String typeName) {
      try (Consumer<String, String> consumer = consumerFor(topic)) {
        // when
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        // then
        ConsumerRecord<String, String> record =
            KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(10));

        assertThat(typeIdOf(record)).isEqualTo(typeName);
        assertThat(record.value()).contains(event.eventId().toString());
      }
    }
  }

  @Nested
  @DisplayName("롤백 이후 미발행")
  class NoPublishOnRollback {

    @Test
    @DisplayName("팔로우 생성 이벤트가 롤백되면 토픽에 아무것도 안 나가는지")
    void doesNotPublishFollowCreated() {
      // given
      UUID followId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      UUID followerId = UUID.randomUUID();

      try (Consumer<String, String> consumer = consumerFor(FOLLOW_TOPIC)) {
        // when
        transactionTemplate.executeWithoutResult(status -> {
          eventPublisher.publishEvent(
              new FollowCreatedEvent(
                  UUID.randomUUID(), Instant.now(), followId, followeeId, followerId, "아메리카노좋아"));
          status.setRollbackOnly();
        });

        // then
        ConsumerRecords<String, String> records =
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));

        assertThat(records.count()).isZero();
      }
    }

    @Test
    @DisplayName("콘텐츠 추가 이벤트가 롤백되면 토픽에 아무것도 안 나가는지")
    void doesNotPublishPlaylistContentAdded() {
      // given
      UUID playlistContentId = UUID.randomUUID();
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();

      try (Consumer<String, String> consumer = consumerFor(PLAYLIST_CONTENT_TOPIC)) {
        // when
        transactionTemplate.executeWithoutResult(status -> {
          eventPublisher.publishEvent(
              new PlaylistContentAddedEvent(
                  UUID.randomUUID(), Instant.now(), playlistContentId, playlistId, contentId, "테스트 플레이리스트"));
          status.setRollbackOnly();
        });

        // then
        ConsumerRecords<String, String> records =
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));

        assertThat(records.count()).isZero();
      }
    }
  }
}