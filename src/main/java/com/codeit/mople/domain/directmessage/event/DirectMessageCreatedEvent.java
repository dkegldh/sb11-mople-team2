package com.codeit.mople.domain.directmessage.event;

import java.util.UUID;

public record DirectMessageCreatedEvent(
    UUID receiverId,
    UUID directMessageId
) {

}
