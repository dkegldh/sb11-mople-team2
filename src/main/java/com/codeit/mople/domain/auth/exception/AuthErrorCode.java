package com.codeit.mople.domain.auth.exception;

import com.codeit.mople.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-001", "이메일 또는 비밀번호가 올바르지 않습니다."),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-002", "유효하지 않은 토큰입니다."),
  EXPIRED_SESSION(HttpStatus.UNAUTHORIZED, "AUTH-003", "다른 기기에서 로그인하여 세션이 만료되었습니다."),
  LOCKED_ACCOUNT(HttpStatus.UNAUTHORIZED, "AUTH-004", "잠긴 계정입니다."),
  EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH-005", "이미 다른 방식으로 가입된 이메일입니다."),
  OAUTH_PROVIDER_ID_MISSING(HttpStatus.UNAUTHORIZED, "AUTH-006", "OAuth 제공자로부터 사용자 식별자를 받지 못했습니다."),
  UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH-007", "지원하지 않는 로그인 방식입니다."),
  TOO_MANY_RESET_PASSWORD_REQUEST(HttpStatus.TOO_MANY_REQUESTS, "AUTH-008", "잠시 후 다시 시도해 주세요.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
