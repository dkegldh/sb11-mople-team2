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
  LOCKED_ACCOUNT(HttpStatus.FORBIDDEN, "USER-003", "잠긴 계정입니다."),
  PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "USER-004", "현재 비밀번호가 일치하지 않습니다."),
  FORBIDDEN_ACCESS(HttpStatus.FORBIDDEN, "USER-005", "본인만 접근할 수 있습니다."),
  INVALID_CURSOR(HttpStatus.BAD_REQUEST, "USER-006", "유효하지 않은 커서 값입니다."),
  CANNOT_MODIFY_SELF(HttpStatus.BAD_REQUEST, "USER-007", "자신의 계정은 변경할 수 없습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
