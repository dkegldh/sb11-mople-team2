package com.codeit.mople.domain.notification.dto.response;

import java.util.List;
import java.util.UUID;

public record CursorResponseNotificationDto(
    List<NotificationResponse> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection
) {

}
