package com.codeit.mople.domain.directmessage.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record DirectMessageCreatedEvent(
    UUID eventId,
    UUID receiverId,
    UUID directMessageId
) implements PublishableEvent {

}
