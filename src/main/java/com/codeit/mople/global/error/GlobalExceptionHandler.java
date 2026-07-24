package com.codeit.mople.global.error;

import com.codeit.mople.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // 직접 정의한 비즈니스 예외
  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
    log.warn("CustomException: {}", e.getMessage());
    ErrorCode errorCode = e.getErrorCode();
    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
  }

  // @Valid 검증 실패 (요청 body)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .orElse(CommonErrorCode.INVALID_INPUT.getMessage());
    log.warn("Validation failed: {}", message);
    return ResponseEntity
        .status(CommonErrorCode.INVALID_INPUT.getStatus())
        .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT.getCode(), message));
  }

  // @Valid 검증 실패 (쿼리 파라미터, path variable 등)
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
    log.warn("ConstraintViolation: {}", e.getMessage());
    return ResponseEntity
        .status(CommonErrorCode.INVALID_INPUT.getStatus())
        .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT.getCode(), e.getMessage()));
  }

  // 예상 못 한 모든 예외
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("Unhandled exception", e);
    CommonErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
  }
}
