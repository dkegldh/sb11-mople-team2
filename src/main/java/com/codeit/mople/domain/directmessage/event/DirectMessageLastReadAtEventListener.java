package com.codeit.mople.domain.directmessage.event;

import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.directmessage.repository.DirectMessageReadRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectMessageLastReadAtEventListener {

  private final DirectMessageReadRedisRepository readRedisRepository;
  private final ConversationRepository conversationRepository;

  // DB 트랜잭션이 성공적으로 커밋된 직후에만 실행
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  // DB Fallback 시 독립적인 새 트랜잭션을 염 (sendMessage -> DirectMessageLastReadAtEventListener)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleLastReadAtUpdate(DirectMessageLastReadAtEvent event) {
    log.debug("DB 커밋 완료: Redis 읽음 워터마크 갱신 시작 - conversationId: {}", event.conversationId());

    boolean isRedisAlive = readRedisRepository.saveLastReadAt(event.conversationId(),
        event.userId(), event.lastReadAt());

    if (!isRedisAlive) {
      conversationRepository.findById(event.conversationId()).ifPresent(conversation -> {
        conversation.updateLastReadAt(event.userId(), event.lastReadAt());
        log.error("Redis 장애 감지: DB에 직접 읽음 시각 업데이트 (Fallback) 완료 - conversationId: {}",
            event.conversationId());
      });
    } else {
      log.info("Redis 갱신: 발신자 DM 읽음 시각 자동 갱신 완료 - conversationId: {}", event.conversationId());
    }
  }
}
