package com.codeit.mople.domain.notification.dto.response;

import java.util.List;
import java.util.UUID;

public record CursorResponseNotificationDto(
    List<NotificationResponse> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    // 알림은 삭제가 곧 읽음 처리이므로, 실질적으로 안 읽은 알림 개수를 의미함. count 쿼리 비용 때문에 첫 페이지(cursor 없음)에서만 계산하고 이후 페이지는 null
    Long totalCount,
    String sortBy,
    String sortDirection
) {

}
