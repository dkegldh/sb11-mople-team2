package com.codeit.mople.global.error;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
  HttpStatus getStatus();
  String getCode();      // 예: "USER-001"
  String getMessage();
}
