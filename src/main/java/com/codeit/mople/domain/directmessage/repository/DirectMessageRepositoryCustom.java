package com.codeit.mople.domain.directmessage.repository;

import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DirectMessageRepositoryCustom {
  List<DirectMessage> findDirectMessageByCursor(UUID conversationId, DirectMessageCursorRequest request, Instant cursorTime);

}
