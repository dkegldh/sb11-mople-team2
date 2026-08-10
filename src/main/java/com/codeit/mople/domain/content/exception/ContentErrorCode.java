package com.codeit.mople.domain.content.exception;

import com.codeit.mople.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ContentErrorCode implements ErrorCode {
  INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "CONTENT-001", "지원하지 않는 콘텐츠 타입입니다."),
  INVALID_PAGE_REQUEST(HttpStatus.BAD_REQUEST, "CONTENT-002", "조회 가능한 개수(limit)는 1 이상이어야 합니다."),
  CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT-003", "콘텐츠를 찾을 수 없습니다."),
  INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "CONTENT-004", "이미지 파일(jpg, png, webp, gif)만 업로드할 수 있습니다."),
  IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT-005", "이미지 업로드 중 오류가 발생했습니다."),

  TMDB_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "CONTENT-006", "콘텐츠 정보를 불러오지 못했습니다."),
  TMDB_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT-007", "요청한 콘텐츠를 찾을 수 없습니다."),
  TMDB_REQUEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT-008", "콘텐츠 정보를 불러오지 못했습니다."),
  TMDB_TEMPORARILY_UNAVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT-009", "콘텐츠 서비스가 불안정합니다. 잠시 후 다시 시도해주세요.");


  private final HttpStatus status;
  private final String code;
  private final String message;
}
