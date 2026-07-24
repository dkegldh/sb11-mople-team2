package com.codeit.mople.domain.user.exception;

import com.codeit.mople.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "사용자를 찾을 수 없습니다."),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER-002", "이미 사용 중인 이메일입니다."),
  LOCKED_ACCOUNT(HttpStatus.FORBIDDEN, "USER-003", "잠긴 계정입니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
