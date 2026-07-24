package com.codeit.mople.global.error;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

  private final ErrorCode errorCode;

  public CustomException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  // 사용 예시 : User user = userRepository.findById(userId)
  //        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
}