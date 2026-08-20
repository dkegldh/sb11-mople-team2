package com.codeit.mople.domain.follow.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.codeit.mople.global.config.KafkaProperties;
import com.codeit.mople.global.event.KafkaEventPublisher;
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
@DisplayName("팔로우 생성 이벤트 Kafka 릴레이 테스트")
class FollowCreatedEventRelayTest {

  private static final String TOPIC = "mople.follow.created.v1";

  @Mock
  KafkaEventPublisher publisher;

  @Captor
  ArgumentCaptor<FollowCreatedMessage> messageCaptor;

  FollowCreatedEventRelay relay;

  UUID followId;
  UUID followeeId;
  UUID followerId;
  FollowCreatedEvent event;

  @BeforeEach
  void setUp() {
    KafkaProperties properties = new KafkaProperties(
        true,
        new KafkaProperties.Topics(
            TOPIC,
            "mople.playlist.content-added.v1",
            "mople.direct-message.created.v1",
            "mople.notification.created.v1"));

    relay = new FollowCreatedEventRelay(publisher, properties);

    followId = UUID.randomUUID();
    followeeId = UUID.randomUUID();
    followerId = UUID.randomUUID();
    event = new FollowCreatedEvent(followId, followeeId, followerId, "아메리카노좋아");
  }

  @Nested
  @DisplayName("이벤트 발행")
  class Relay {

    @Test
    @DisplayName("설정된 토픽으로 발행하는지")
    void publishesToConfiguredTopic() {
      // when
      relay.relay(event);

      // then
      verify(publisher).publish(eq(TOPIC),
          anyString(), messageCaptor.capture());
    }

    @Test
    @DisplayName("파티션 키가 followeeId 인지")
    void usesFolloweeIdAsPartitionKey() {
      // when
      relay.relay(event);

      // then
      verify(publisher).publish(eq(TOPIC), eq(followeeId.toString()), any(FollowCreatedMessage.class));
    }

    @Test
    @DisplayName("원본 이벤트의 값이 페이로드에 그대로 실리는지")
    void carriesEventPayload() {
      // when
      relay.relay(event);

      // then
      verify(publisher).publish(eq(TOPIC),
          anyString(), messageCaptor.capture());

      FollowCreatedMessage message = messageCaptor.getValue();
      assertThat(message.followId()).isEqualTo(followId);
      assertThat(message.followeeId()).isEqualTo(followeeId);
      assertThat(message.followerId()).isEqualTo(followerId);
      assertThat(message.followerName()).isEqualTo("아메리카노좋아");
    }
  }

  @Nested
  @DisplayName("메시지 변환")
  class MessageMapping {

    @Test
    @DisplayName("발행 시각과 이벤트 id를 채우는지")
    void fillsEventIdAndOccurredAt() {
      // when
      FollowCreatedMessage message = FollowCreatedMessage.from(event);

      // then
      assertThat(message.eventId()).isNotNull();
      assertThat(message.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("이벤트 id가 호출마다 새로 생성되는지")
    void generatesNewEventIdPerCall() {
      // when
      FollowCreatedMessage first = FollowCreatedMessage.from(event);
      FollowCreatedMessage second = FollowCreatedMessage.from(event);

      // then
      assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }
  }
}