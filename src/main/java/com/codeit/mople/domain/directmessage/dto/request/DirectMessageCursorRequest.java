package com.codeit.mople.domain.directmessage.dto.request;

import com.codeit.mople.domain.directmessage.exception.DirectMessageException;
import com.codeit.mople.global.error.CommonErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DirectMessageCursorRequest(
    String cursor,
    UUID idAfter,

    @Min(value = 1, message = "페이지 크기는 최소 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 최대 100을 초과할 수 없습니다.")
    Integer limit,

    @Pattern(regexp = "^(DESCENDING)$", message = "정렬 방향은 'DESCENDING'만 가능합니다.")
    String sortDirection,

    @Pattern(regexp = "^(createdAt)$", message = "정렬 기준은 'createdAt'만 가능합니다.")
    String sortBy
) {

  public DirectMessageCursorRequest {
    if (limit == null) {
      limit = 20;
    }
    if (sortDirection == null || sortDirection.isBlank()) {
      sortDirection = "DESCENDING";
    }
    if (sortBy == null || sortBy.isBlank()) {
      sortBy = "createdAt";
    }

    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null;

    if (hasCursor != hasIdAfter) {
      throw new DirectMessageException(
          CommonErrorCode.INVALID_INPUT, Map.of("message", "커서 페이싱 시 cursor와 idAfter는 함께 제공해야 합니다."));
    }
  }

  public Instant parseCursorToInstant() {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(cursor);
    } catch (Exception e) {
      throw new DirectMessageException(CommonErrorCode.INVALID_INPUT,
          Map.of("invalidCursor", cursor));
    }
  }
}
