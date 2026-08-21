package com.codeit.mople.domain.directmessage.service;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectMessageReadSyncService {

  private final ConversationRepository conversationRepository;

  @Transactional
  public void syncToDb(UUID conversationId, UUID userId, Instant lastReadAt) {
    Conversation conversation = conversationRepository.findById(conversationId).orElse(null);

    if (conversation != null) {
      conversation.updateLastReadAt(userId, lastReadAt);
      log.debug("읽음 워터마크 DB 갱신 커밋 완료 - conversationId: {}, userId: {}", conversationId, userId);
    }
  }

}
