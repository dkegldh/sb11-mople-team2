package com.codeit.mople.domain.user.dto.request;

import java.util.Arrays;
import java.util.NoSuchElementException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserSortBy {
  NAME("name"),
  EMAIL("email"),
  CREATED_AT("createdAt"),
  IS_LOCKED("isLocked"),
  ROLE("role");

  private final String value;

  public static UserSortBy from(String value) {
    return Arrays.stream(values())
        .filter(sortBy -> sortBy.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("지원하지 않는 정렬 기준입니다: " + value));
  }
}
