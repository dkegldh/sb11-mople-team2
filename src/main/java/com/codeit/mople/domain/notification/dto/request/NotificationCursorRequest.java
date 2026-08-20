package com.codeit.mople.domain.notification.dto.request;

import com.codeit.mople.domain.notification.exception.NotificationErrorCode;
import com.codeit.mople.domain.notification.exception.NotificationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationCursorRequest(
    String cursor,
    UUID idAfter,

    @Min(value = 1, message = "페이지 크기는 최소 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 최대 100을 초과할 수 없습니다.")
    Integer limit
) {

    // 기본값 대입은 예외를 던지지 않으므로 BeanInstantiationException 문제와 무관함 (DirectMessage/Conversation과 동일한 기본값)
    public NotificationCursorRequest {
        if (limit == null) {
            limit = 20;
        }
    }

    // cursor/idAfter 짝 검증을 compact constructor에서 하면 예외가 BeanInstantiationException으로 감싸지므로,
    // 서비스가 명시적으로 호출하는 일반 메서드로 둠 (parseCursorToInstant()와 동일한 방식)
    public void validateCursorPair() {
        boolean hasCursor = cursor != null && !cursor.isBlank();
        boolean hasIdAfter = idAfter != null;

        if (hasCursor != hasIdAfter) {
            throw new NotificationException(
                NotificationErrorCode.NOTIFICATION_INVALID_CURSOR_PAIR,
                Map.of("message", "커서 페이징 시 cursor와 idAfter는 함께 제공해야 합니다."));
        }
    }

    public Instant parseCursorToInstant() {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(cursor);
        } catch (Exception e) {
            throw new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_CURSOR,
                Map.of("invalidCursor", cursor));
        }
    }
}
