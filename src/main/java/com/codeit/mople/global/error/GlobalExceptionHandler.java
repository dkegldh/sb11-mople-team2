package com.codeit.mople.global.error;

import com.codeit.mople.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

  // Record 생성자에서 던진 CustomException이 BeanInstantiationException으로 감싸진 경우
  @ExceptionHandler(BeanInstantiationException.class)
  public ResponseEntity<ApiResponse<Void>> handleBeanInstantiationException(
      BeanInstantiationException e) {
    if (e.getCause() instanceof CustomException customException) {
      return handleCustomException(customException);
    }
    return handleException(e);
  }

  // 경로 변수/파라미터 타입 변환 실패 (e.g. UUID 형식 오류)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
      MethodArgumentTypeMismatchException e) {
    log.warn("[TypeMismatch] Parameter: {}, ExpectedType: {}", e.getName(), e.getRequiredType());
    return ResponseEntity
        .status(CommonErrorCode.INVALID_INPUT.getStatus())
        .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT.getCode(),
            CommonErrorCode.INVALID_INPUT.getMessage()));
  }

  // @Valid 검증 실패 (요청 body)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException e) {
    for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
      if (!fieldError.contains(Throwable.class)) {
        continue;
      }
      CustomException cause = findCustomExceptionCause(fieldError.unwrap(Throwable.class));
      if (cause != null) {
        return handleCustomException(cause);
      }
    }

    String message = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .orElse(CommonErrorCode.INVALID_INPUT.getMessage());
    log.warn("[Validation failed] Message: {}", message);
    return ResponseEntity
        .status(CommonErrorCode.INVALID_INPUT.getStatus())
        .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT.getCode(), message));
  }

  // 커스텀 Converter(예: sortBy 카멜케이스 변환)가 던진 CustomException은
  // DataBinder가 TypeMismatchException -> ConversionFailedException 순으로 감싸서 바인딩 오류로 만든다.
  // 원래의 도메인 에러 코드/메시지를 응답에 살리기 위해 원인 체인을 직접 순회한다.
  private CustomException findCustomExceptionCause(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof CustomException customException) {
        return customException;
      }
      current = current.getCause();
    }
    return null;
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

  // 인가(권한) 검증 실패 시 403 Forbidden 반환
  @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
  public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(Exception e) {
    log.warn("[AccessDenied] Message: {}", e.getMessage());
    CommonErrorCode errorCode = CommonErrorCode.FORBIDDEN;
    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
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

  //경로 변수(PathVariable)나 쿼리 파라미터(RequestParam)의 타입 변환 실패 시 발생
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException e) {
    log.warn("[TypeMismatch] Parameter: {}, Message: {}", e.getPropertyName(), e.getMessage());
    return ResponseEntity
        .status(CommonErrorCode.INVALID_INPUT.getStatus())
        .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT.getCode(), "파라미터 타입이 올바르지 않습니다."));
  }
}
