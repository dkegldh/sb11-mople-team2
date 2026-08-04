package com.codeit.mople.domain.follow.exception;

import com.codeit.mople.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FollowErrorCode implements ErrorCode {

  FOLLOW_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "FOLLOW-001", "자기 자신을 팔로우할 수 없습니다."),
  FOLLOW_DUPLICATE(HttpStatus.BAD_REQUEST, "FOLLOW-002", "이미 팔로우 중인 사용자입니다."),
  FOLLOW_FOLLOWEE_NOT_FOUND(HttpStatus.BAD_REQUEST, "FOLLOW-003", "팔로우 대상 사용자를 찾을 수 없습니다."),
  FOLLOW_FOLLOWER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "FOLLOW-004", "요청 사용자를 찾을 수 없습니다."),

  UNFOLLOW_NOT_FOUND(HttpStatus.BAD_REQUEST, "FOLLOW-005", "팔로우를 찾을 수 없습니다."),
  UNFOLLOW_NOT_OWNER(HttpStatus.FORBIDDEN, "FOLLOW-006", "본인의 팔로우만 취소 가능합니다."),

  FOLLOW_BY_ME_NOT_FOUND(HttpStatus.NOT_FOUND, "FOLLOW-007", "팔로우하고 있지 않은 사용자입니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
