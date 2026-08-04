package com.codeit.mople.domain.playlist.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PlaylistQueryCondition(

    // 제목 검색어(키워드)
    String keywordLike,

    // 소유자 ID
    UUID ownerIdEqual,

    // 구독자 ID
    UUID subscriberIdEqual,
    
    // 커서
    String cursor,
    
    // 보조 커서
    UUID idAfter,

    @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
    @Max(value = 100, message = "limit는 100 이하여야 합니다.")
    @NotNull(message = "limit는 필수입니다.")
    Integer limit, // 1회 조회로 가져올 최대 개수

    @NotNull(message = "정렬 방향은 필수입니다.")
    SortDirection sortDirection, // 정렬 방향

    @NotNull(message = "정렬 조건은 필수입니다.")
    PlaylistSortBy sortBy // 정렬 기준
) {

  public enum SortDirection {
    ASCENDING, // 오름차순
    DESCENDING // 내림차순
  }

  public enum PlaylistSortBy {
    UPDATED_AT, // 최신순
    SUBSCRIBER_COUNT // 구독순
  }

}
