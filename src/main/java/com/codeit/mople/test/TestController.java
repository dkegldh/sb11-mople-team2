package com.codeit.mople.test;

import com.codeit.mople.global.error.CommonErrorCode;
import com.codeit.mople.global.error.CustomException;
import com.codeit.mople.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

  // 1. 정상 응답 확인용
  @GetMapping("/api/test/success")
  public ApiResponse<String> testSuccess() {
    return ApiResponse.success("정상 응답입니다.");
  }

  // 2. 예외 처리 흐름 확인용
  @GetMapping("/api/test/error")
  public void testError() {
    throw new CustomException(CommonErrorCode.NOT_FOUND);
  }
}
