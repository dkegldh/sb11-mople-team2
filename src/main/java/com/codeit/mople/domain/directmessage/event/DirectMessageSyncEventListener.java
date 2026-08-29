package com.codeit.mople.domain.directmessage.event;

import com.codeit.mople.domain.directmessage.document.DirectMessageDocument;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.directmessage.exception.DirectMessageErrorCode;
import com.codeit.mople.domain.directmessage.exception.DirectMessageException;
import com.codeit.mople.domain.directmessage.repository.DirectMessageRepository;
import com.codeit.mople.domain.directmessage.repository.DirectMessageSearchRepository;
import com.codeit.mople.global.config.KafkaProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = KafkaProperties.PREFIX, name = "enabled", havingValue = "true")
public class DirectMessageSyncEventListener {

  private final DirectMessageSearchRepository directMessageSearchRepository;
  private final DirectMessageRepository directMessageRepository;

  @KafkaListener(
      topics = "${spring.kafka.topics.direct-message-created}",
      groupId = "${mople.kafka.consumer.es-sync-group-id}"
  )
  public void handleDirectMessageCreatedForSearch(DirectMessageCreatedEvent event) {
    log.debug("Elasticsearch 검색 동기화 이벤트 수신 - directMessageId: {}", event.directMessageId());

    try {
      DirectMessage message = directMessageRepository.findById(event.directMessageId())
          .orElseThrow(() -> new DirectMessageException(DirectMessageErrorCode.DIRECT_MESSAGE_NOT_FOUND, Map.of("directMessageId", event.directMessageId())));

      DirectMessageDocument document = DirectMessageDocument.from(message);
      directMessageSearchRepository.save(document);

      log.info("Elasticsearch 메시지 저장 완료 - directMessageId: {}", event.directMessageId());
    } catch (Exception e){
      log.error("Elasticsearch 메시지 저장 중 에러 발생 (Kafka) - directMessageId: {}", event.directMessageId(), e);
      throw new RuntimeException("ES 동기화 실패로 Kafka 재시도를 요청합니다.", e);
    }
  }
}
