package com.codeit.mople.domain.content.exception;

import com.codeit.mople.global.error.CustomException;
import com.codeit.mople.global.error.ErrorCode;
import java.util.Map;

public class ContentException extends CustomException {

  public ContentException(ErrorCode errorCode) {
    super(errorCode);
  }

  public ContentException(ErrorCode errorCode, Map<String, Object> details) {
    super(errorCode, details);
  }
}
