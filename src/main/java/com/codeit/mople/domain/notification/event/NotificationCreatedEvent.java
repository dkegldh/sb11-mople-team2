package com.codeit.mople.domain.notification.event;

import java.util.UUID;

public record NotificationCreatedEvent(
    UUID receiverId,
    UUID notificationId
) {

}
