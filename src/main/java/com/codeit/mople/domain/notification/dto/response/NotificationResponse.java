package com.codeit.mople.domain.notification.dto.response;

import com.codeit.mople.domain.notification.entity.NotificationLevel;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    Instant createdAt,
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {}