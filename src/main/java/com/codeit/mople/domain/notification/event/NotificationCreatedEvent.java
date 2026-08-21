package com.codeit.mople.domain.notification.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record NotificationCreatedEvent(
    UUID eventId,
    UUID receiverId,
    UUID notificationId
) implements PublishableEvent {

}
