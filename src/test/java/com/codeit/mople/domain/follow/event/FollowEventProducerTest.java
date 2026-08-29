package com.codeit.mople.domain.follow.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.codeit.mople.global.config.KafkaProperties;
import com.codeit.mople.global.event.KafkaEventPublisher;
import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("팔로우 생성 이벤트 Kafka 발행 테스트")
class FollowEventProducerTest {

  private static final String TOPIC = "mople.follow.created.v1";

  @Mock
  KafkaEventPublisher publisher;

  @Captor
  ArgumentCaptor<FollowCreatedEvent> eventCaptor;

  FollowEventProducer producer;

  UUID followId;
  UUID followeeId;
  UUID followerId;
  FollowCreatedEvent event;

  @BeforeEach
  void setUp() {
    KafkaProperties properties = new KafkaProperties(
        true,
        "localhost:9092",
        new KafkaProperties.Topics(
            "mople.follow.created.v1",
            "mople.playlist-events.v1",
            "mople.playlist.content-added.v1",
            "mople.review.created.v1",
            "mople.review.updated.v1",
            "mople.review.deleted.v1",
            "mople.direct-message.created.v1",
            "mople.notification.created.v1",
            "mople.content-search-index-events.v1",
            "mople.user-search-index-events.v1",
            "mople.playlist-search-index-events.v1"
        )
    );

    producer = new FollowEventProducer(publisher, properties);

    followId = UUID.randomUUID();
    followeeId = UUID.randomUUID();
    followerId = UUID.randomUUID();
    event = new FollowCreatedEvent(
        UUID.randomUUID(), Instant.now(), followId, followeeId, followerId, "아메리카노좋아");
  }

  @Nested
  @DisplayName("이벤트 발행")
  class Publish {

    @Test
    @DisplayName("설정된 토픽으로 발행하는지")
    void publishesToConfiguredTopic() {
      // when
      producer.on(event);

      // then
      verify(publisher).publish(eq(TOPIC),
          anyString(), eventCaptor.capture());
    }

    @Test
    @DisplayName("파티션 키가 followeeId 인지")
    void usesFolloweeIdAsPartitionKey() {
      // when
      producer.on(event);

      // then
      verify(publisher).publish(eq(TOPIC), eq(followeeId.toString()), any(FollowCreatedEvent.class));
    }

    @Test
    @DisplayName("원본 이벤트가 그대로 페이로드로 실리는지")
    void carriesEventPayload() {
      // when
      producer.on(event);

      // then
      verify(publisher).publish(eq(TOPIC),
          anyString(), eventCaptor.capture());

      FollowCreatedEvent published = eventCaptor.getValue();
      assertThat(published.eventId()).isEqualTo(event.eventId());
      assertThat(published.occurredAt()).isEqualTo(event.occurredAt());
      assertThat(published.followId()).isEqualTo(followId);
      assertThat(published.followeeId()).isEqualTo(followeeId);
      assertThat(published.followerId()).isEqualTo(followerId);
      assertThat(published.followerName()).isEqualTo("아메리카노좋아");
    }
  }
}