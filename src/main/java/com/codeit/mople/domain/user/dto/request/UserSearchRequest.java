package com.codeit.mople.domain.user.dto.request;

import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.global.dto.SortDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record UserSearchRequest(
    String emailLike,
    Role roleEqual,
    Boolean isLocked,
    String cursor,
    UUID idAfter,
    @Min(value = 1, message = "limit은 1 이상이어야 합니다.")
    @Max(value = 100, message = "limit은 100 이하여야 합니다.")
    Integer limit,
    SortDirection sortDirection,
    UserSortBy sortBy
) {
  private static final int DEFAULT_LIMIT = 20;

  public int limitOrDefault() {
    return limit == null ? DEFAULT_LIMIT : limit;
  }

  public SortDirection sortDirectionOrDefault() {
    return sortDirection == null ? SortDirection.ASCENDING : sortDirection;
  }

  public UserSortBy sortByOrDefault() {
    return sortBy == null ? UserSortBy.NAME : sortBy;
  }
}
