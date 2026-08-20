package com.codeit.mople.domain.review.dto.response;

import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition.ReviewSortBy;
import com.codeit.mople.global.dto.SortDirection;
import java.util.List;
import java.util.UUID;

public record ReviewCursorResponse(
    List<ReviewResponse> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    ReviewSortBy sortBy,
    SortDirection sortDirection
) {

}
