package com.codeit.mople.domain.user.dto.request;

import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.global.dto.SortDirection;
import java.util.UUID;

public record UserSearchRequest(
    String emailLike,
    Role roleEqual,
    Boolean isLocked,
    String cursor,
    UUID idAfter,
    Integer limit,
    SortDirection sortDirection,
    UserSortBy sortBy
) {
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  public int limitOrDefault() {
    if(limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  public SortDirection sortDirectionOrDefault() {
    return sortDirection == null ? SortDirection.ASCENDING : sortDirection;
  }

  public UserSortBy sortByOrDefault() {
    return sortBy == null ? UserSortBy.name : sortBy;
  }
}
