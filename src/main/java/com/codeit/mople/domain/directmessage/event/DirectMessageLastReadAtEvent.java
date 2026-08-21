package com.codeit.mople.domain.directmessage.event;

import java.time.Instant;
import java.util.UUID;

public record DirectMessageLastReadAtEvent(
    UUID conversationId,
    UUID userId,
    Instant lastReadAt
) {

}
