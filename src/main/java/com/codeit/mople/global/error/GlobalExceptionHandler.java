package com.codeit.mople.global.error;

import com.codeit.mople.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final Map<String, ErrorCode> constraintErrorCodes;

  // 직접 정의한 비즈니스 예외
  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
    ErrorCode errorCode = e.getErrorCode();
    log.warn("[CustomException] Type: {}, Code: {}, Message: {}, Details: {}", e.getClass().getSimpleName(), errorCode.getCode(), e.getMessage(), LogMaskingUtils.maskSensitiveDetails(e.getDetails()));
    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), e.getDetails()));
  }

  // @Valid 검증 실패 (요청 body)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .orElse(CommonErrorCode.INVALID_INPUT.getMessage());
    log.warn("[Validation failed] Message: {}", message);
    return ResponseEntity
        .status(CommonErrorCode.INVALID_INPUT.getStatus())
        .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT.getCode(), message));
  }

  // @Valid 검증 실패 (쿼리 파라미터, path variable 등)
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
      ConstraintViolationException e) {
    log.warn("[ConstraintViolation] Message: {}", e.getMessage());
    return ResponseEntity
        .status(CommonErrorCode.INVALID_INPUT.getStatus())
        .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT.getCode(), e.getMessage()));
  }

  // 예상 못 한 모든 예외
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("[Unhandled exception] Message: {}", e.getMessage(), e);
    CommonErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
  }

  // DB 제약 위반 (동시 요청으로 사전 검증을 통과한 경우 등)
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
      DataIntegrityViolationException e
  ) {
    ErrorCode errorCode = resolveConstraint(e);

    if (errorCode == null) {
      log.error("[Unhandled DataIntegrityViolation] Message: {}", e.getMessage(), e);
      errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
    } else {
      log.warn("[DataIntegrityViolation] Code: {}, Message: {}", errorCode.getCode(), errorCode.getMessage());
    }

    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
  }

  private ErrorCode resolveConstraint(DataIntegrityViolationException e) {
    if (!(e.getCause() instanceof org.hibernate.exception.ConstraintViolationException ce)
        || ce.getConstraintName() == null) {
      return null;
    }
    String name = ce.getConstraintName().toLowerCase();
    return constraintErrorCodes.entrySet().stream()
        .filter(entry -> name.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  public GlobalExceptionHandler(List<ConstraintErrorCodes> contributors) {
    this.constraintErrorCodes = contributors.stream()
        .flatMap(c -> c.get().entrySet().stream())
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
