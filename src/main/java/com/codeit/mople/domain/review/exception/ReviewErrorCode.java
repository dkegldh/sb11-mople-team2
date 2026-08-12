package com.codeit.mople.domain.review.exception;

import com.codeit.mople.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

  REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW-001", "리뷰를 찾을 수 없습니다."),
  REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "REVIEW-002", "리뷰에 대한 접근 권한이 없습니다."),
  REVIEW_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "REVIEW-003", "유효하지 않은 커서 값입니다."),
  REVIEW_INVALID_SORT_BY(HttpStatus.BAD_REQUEST, "REVIEW-004", "유효하지 않은 정렬 조건입니다."),;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
