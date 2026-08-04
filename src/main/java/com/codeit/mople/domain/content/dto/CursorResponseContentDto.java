package com.codeit.mople.domain.content.dto;

import java.util.List;
import java.util.UUID;

public record CursorResponseContentDto(
    List<ContentResponse> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection
) {

}