package com.codeit.mople.domain.content.event;

import com.codeit.mople.domain.content.repository.search.ContentDocument;
import com.codeit.mople.domain.content.repository.search.ContentSearchRepository;
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
@KafkaListener(topics = "content-search-index-events")
public class ContentSearchIndexConsumer {

  private final ContentSearchRepository contentSearchRepository;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaHandler
  @Transactional
  public void handle(ContentSearchIndexEvent event) {
    log.debug("콘텐츠 검색 인덱스 반영 시도: contentId={}",
        event.contentId());

    if (checkAndRecordProcessedEvent(event.eventId())) {
      return;
    }

    contentSearchRepository.save(
        new ContentDocument(
            event.contentId(),
            event.title()
        )
    );

    log.info("콘텐츠 검색 인덱스 반영 완료: contentId={}",
        event.contentId());
  }

  @KafkaHandler
  @Transactional
  public void handle(ContentSearchIndexDeleteEvent event) {
    log.debug("콘텐츠 검색 인덱스 삭제 시도: contentId={}",
        event.contentId());

    if (checkAndRecordProcessedEvent(event.eventId())) {
      return;
    }

    contentSearchRepository.deleteById(event.contentId());

    log.info("콘텐츠 검색 인덱스 삭제 완료: contentId={}",
        event.contentId());
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