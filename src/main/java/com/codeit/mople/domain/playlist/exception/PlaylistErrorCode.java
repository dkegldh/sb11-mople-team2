package com.codeit.mople.domain.playlist.exception;

import com.codeit.mople.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PlaylistErrorCode implements ErrorCode {

  PLAYLIST_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "PLAYLIST-001", "인증이 필요합니다."),
  PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAYLIST-002", "플레이리스트를 찾을 수 없습니다."),
  PLAYLIST_FORBIDDEN(HttpStatus.FORBIDDEN, "PLAYLIST-003", "플레이리스트에 대한 접근 권한이 없습니다."),
  PLAYLIST_UPDATE_BLANK_TITLE(HttpStatus.BAD_REQUEST, "PLAYLIST-004", "제목을 입력해주세요."),
  PLAYLIST_UPDATE_BLANK_DESCRIPTION(HttpStatus.BAD_REQUEST, "PLAYLIST-005", "설명을 입력해주세요."),

  SUBSCRIBE_NOT_FOUND(HttpStatus.BAD_REQUEST, "PLAYLIST-006", "플레이리스트를 찾을 수 없습니다."),
  SUBSCRIBE_NOT_ALLOWED(HttpStatus.BAD_REQUEST,"PLAYLIST-007","본인의 플레이리스트는 구독할 수 없습니다."),
  SUBSCRIBE_USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "PLAYLIST-008", "요청 사용자를 찾을 수 없습니다."),
  SUBSCRIBE_DUPLICATE(HttpStatus.BAD_REQUEST, "PLAYLIST-009", "이미 구독한 플레이리스트입니다."),

  UNSUBSCRIBE_NOT_FOUND(HttpStatus.BAD_REQUEST,"PLAYLIST-010","구독하지 않은 플레이리스트입니다."),

  PLAYLIST_CONTENT_PLAY_NOT_FOUND(HttpStatus.BAD_REQUEST, "PLAYLIST-011", "플레이리스트를 찾을 수 없습니다."),
  PLAYLIST_CONTENT_CONTENT_NOT_FOUND(HttpStatus.BAD_REQUEST,"PLAYLIST-012","존재하지 않는 콘텐츠입니다."),
  PLAYLIST_CONTENT_DUPLICATE(HttpStatus.BAD_REQUEST,"PLAYLIST-013","이미 플레이리스트에 담긴 콘텐츠입니다."),

  UN_PLAYLIST_CONTENT_NOT_FOUND(HttpStatus.BAD_REQUEST,"PLAYLIST-014","플레이리스트에 콘텐츠가 없습니다."),

  PLAYLIST_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "PLAYLIST-015", "유효하지 않은 커서 값입니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
