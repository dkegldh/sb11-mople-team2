package com.codeit.mople.domain.conversation.repository;

import com.codeit.mople.domain.conversation.dto.request.ConversationCursorRequest;
import com.codeit.mople.domain.conversation.entity.Conversation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConversationRepositoryCustom {
  List<Conversation> findConversationByCursor(UUID requesterId, ConversationCursorRequest request, Instant cursorTime);

}
