package com.codeit.mople.domain.user.event;

import com.codeit.mople.domain.user.repository.search.UserDocument;
import com.codeit.mople.domain.user.repository.search.UserSearchRepository;
import com.codeit.mople.global.config.KafkaProperties;
import com.codeit.mople.global.event.processed.ProcessedEventRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = KafkaProperties.PREFIX,
    name = "enabled",
    havingValue = "true"
)
@KafkaListener(topics = "user-search-index-events")
public class UserSearchIndexConsumer {

  private final UserSearchRepository userSearchRepository;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaHandler
  @Transactional
  public void handle(UserSearchIndexEvent event) {
    log.debug("사용자 검색 인덱스 반영 시도: userId={}",
        event.userId());

    if (checkAndRecordProcessedEvent(event.eventId())) {
      return;
    }

    userSearchRepository.save(
        new UserDocument(
            event.userId(),
            event.email()
        )
    );

    log.info("사용자 검색 인덱스 반영 완료: userId={}",
        event.userId());
  }

  private boolean checkAndRecordProcessedEvent(UUID eventId) {
    int inserted = processedEventRepository.insertIfAbsent(eventId);

    if (inserted == 0) {
      log.info("이미 처리된 이벤트입니다: eventId={}", eventId);
      return true;
    }

    return false;
  }
}