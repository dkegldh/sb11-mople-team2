package com.codeit.mople.domain.follow.exception;

import com.codeit.mople.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FollowErrorCode implements ErrorCode {

  FOLLOW_NOT_FOUND(HttpStatus.BAD_REQUEST, "FOLLOW-001", "팔로우 정보를 찾을 수 없습니다."),
  FOLLOW_DUPLICATE(HttpStatus.BAD_REQUEST, "FOLLOW-002", "이미 팔로우 중인 사용자입니다."),
  FOLLOW_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "FOLLOW-003", "자기 자신을 팔로우할 수 없습니다."),
  FOLLOW_NOT_OWNER(HttpStatus.FORBIDDEN, "FOLLOW-004", "본인의 팔로우만 취소할 수 있습니다."),
  FOLLOWEE_NOT_FOUND(HttpStatus.BAD_REQUEST, "FOLLOW-005", "팔로우 대상 사용자를 찾을 수 없습니다."),
  FOLLOWER_NOT_FOUND(HttpStatus.BAD_REQUEST, "FOLLOW-006", "팔로워는 대상이 아니라 요청자이므로 팔로우 요청 사용자를 찾을 수 없습니다."),
  FOLLOW_BY_ME_NOT_FOUND(HttpStatus.NOT_FOUND, "FOLLOW-007", "팔로우 대상을 찾을 수 없습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
