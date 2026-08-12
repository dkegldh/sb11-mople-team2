package com.codeit.mople.domain.directmessage.event;

import java.util.UUID;

public record DirectMessageReceivedEvent(
    UUID receiverId,
    String senderName,
    String messageContent
) {

}
