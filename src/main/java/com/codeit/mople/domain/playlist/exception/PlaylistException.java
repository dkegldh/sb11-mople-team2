package com.codeit.mople.domain.playlist.exception;

import com.codeit.mople.global.error.CustomException;
import java.util.Map;

public class PlaylistException extends CustomException {

  public PlaylistException(PlaylistErrorCode errorCode) {
    super(errorCode);
  }

  public PlaylistException(PlaylistErrorCode errorCode, Map<String, Object> details) {
    super(errorCode, details);
  }
}
