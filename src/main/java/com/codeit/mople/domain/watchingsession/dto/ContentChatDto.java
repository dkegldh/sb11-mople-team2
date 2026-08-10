package com.codeit.mople.domain.watchingsession.dto;

import java.time.Instant;
import java.util.UUID;

public record ContentChatDto(
    String contentId,
    UUID senderId,
    String senderName,
    String message,
    Instant timestamp
) {

}
