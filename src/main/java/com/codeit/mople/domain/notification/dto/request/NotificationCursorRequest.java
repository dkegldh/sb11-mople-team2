package com.codeit.mople.domain.notification.dto.request;

import com.codeit.mople.domain.notification.exception.NotificationException;
import com.codeit.mople.global.error.CommonErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationCursorRequest(
    String cursor,
    UUID idAfter,

    @NotNull(message = "limit는 필수입니다.")
    @Min(value = 1, message = "페이지 크기는 최소 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 최대 100을 초과할 수 없습니다.")
    Integer limit
) {

    // cursor/idAfter 짝 검증을 compact constructor에서 하면 예외가 BeanInstantiationException으로 감싸지므로,
    // 서비스가 명시적으로 호출하는 일반 메서드로 둠 (parseCursorToInstant()와 동일한 방식)
    public void validateCursorPair() {
        boolean hasCursor = cursor != null && !cursor.isBlank();
        boolean hasIdAfter = idAfter != null;

        if (hasCursor != hasIdAfter) {
            throw new NotificationException(
                CommonErrorCode.INVALID_INPUT, Map.of("message", "커서 페이징 시 cursor와 idAfter는 함께 제공해야 합니다."));
        }
    }

    public Instant parseCursorToInstant() {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(cursor);
        } catch (Exception e) {
            throw new NotificationException(CommonErrorCode.INVALID_INPUT,
                Map.of("invalidCursor", cursor));
        }
    }
}
