package com.codeit.mople.domain.notification.event;

import java.util.UUID;

public record NotificationCreatedEvent(
    UUID eventId,
    UUID receiverId,
    UUID notificationId
) {

}
