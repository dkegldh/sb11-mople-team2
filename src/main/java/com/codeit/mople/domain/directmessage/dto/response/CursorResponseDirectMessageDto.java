package com.codeit.mople.domain.directmessage.dto.response;

import java.util.List;
import java.util.UUID;

public record CursorResponseDirectMessageDto(
    List<DirectMessageDto> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection
) {

}
