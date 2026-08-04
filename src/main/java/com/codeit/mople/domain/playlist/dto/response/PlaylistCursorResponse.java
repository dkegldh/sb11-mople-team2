package com.codeit.mople.domain.playlist.dto.response;

import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.PlaylistSortBy;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.SortDirection;
import java.util.List;
import java.util.UUID;

public record PlaylistCursorResponse(
    List<PlaylistResponse> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    PlaylistSortBy sortBy,
    SortDirection sortDirection
) {

}