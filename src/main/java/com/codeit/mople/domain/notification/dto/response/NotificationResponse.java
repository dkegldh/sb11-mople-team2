package com.codeit.mople.domain.notification.dto.response;

import com.codeit.mople.domain.notification.NotificationLevel;
import com.codeit.mople.domain.notification.entity.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    Instant createdAt,
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {

  public static NotificationResponse from(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getCreatedAt(),
        notification.getReceiver().getId(),
        notification.getTitle(),
        notification.getContent(),
        notification.getLevel()
    );
  }

}