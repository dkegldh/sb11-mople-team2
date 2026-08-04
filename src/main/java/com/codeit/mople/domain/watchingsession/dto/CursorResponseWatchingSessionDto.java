package com.codeit.mople.domain.watchingsession.dto;

import java.util.List;
import java.util.UUID;

public record CursorResponseWatchingSessionDto(
    List<WatchingSessionResponse> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection
) {

}
