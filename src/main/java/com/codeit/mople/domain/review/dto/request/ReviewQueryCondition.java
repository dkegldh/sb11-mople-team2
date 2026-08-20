package com.codeit.mople.domain.review.dto.request;

import com.codeit.mople.global.dto.SortDirection;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReviewQueryCondition(

    // 콘텐츠 ID
    UUID contentId,

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
    ReviewSortBy sortBy // 정렬 기준
) {

  public enum ReviewSortBy {
    CREATED_AT("createdAt"), // 생성 순
    RATING("rating"); // 평점 순

    private final String value;

    ReviewSortBy(String value) {
      this.value = value;
    }

    public static ReviewSortBy from(String value) {
      for (ReviewSortBy sortBy : ReviewSortBy.values()) {
        if (sortBy.value.equals(value)) {
          return sortBy;
        }
      }

      throw new IllegalArgumentException();
    }

    @JsonValue
    public String getValue() {
      return value;
    }
  }
}
