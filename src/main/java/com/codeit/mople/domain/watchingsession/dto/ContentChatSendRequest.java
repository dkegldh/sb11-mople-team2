package com.codeit.mople.domain.watchingsession.dto;

import java.util.UUID;

public record ContentChatSendRequest(
    UUID senderId,
    String senderName,
    String message
) {

}
