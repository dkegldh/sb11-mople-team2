package com.codeit.mople.domain.follow.event;

import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.service.NotificationCreator;
import com.codeit.mople.global.config.KafkaProperties;
import com.codeit.mople.global.event.processed.ProcessedEventRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = KafkaProperties.PREFIX, name = "enabled", havingValue = "true")
public class FollowEventConsumer {

  private final NotificationCreator notificationCreator;
  private final ProcessedEventRepository processedEventRepository;

  @Transactional
  @KafkaListener(topics = "${spring.kafka.topics.follow-created}")
  public void handle(FollowCreatedEvent event) {
    if (checkAndRecordProcessedEvent(event.eventId())) {
      return;
    }

    notificationCreator.createNotification(
        event.followeeId(),
        "새로운 팔로워가 생겼습니다.",
        event.followerName() + "님이 팔로우했습니다.",
        NotificationType.NEW_FOLLOWER
    );

    log.info("팔로우 생성 이벤트 처리 완료: followId={}", event.followId());
  }

  private boolean checkAndRecordProcessedEvent(UUID eventId) {
    // 이미 해당 eventId가 존재하면 스킵
    int inserted = processedEventRepository.insertIfAbsent(eventId);

    if (inserted == 0) {
      log.info("이미 처리된 이벤트입니다: eventId={}", eventId);
      return true;
    }

    return false;
  }

}
