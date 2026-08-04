package com.codeit.mople.domain.playlist.exception;

import com.codeit.mople.global.error.ConstraintErrorCodes;
import com.codeit.mople.global.error.ErrorCode;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PlaylistConstraintErrorCodes implements ConstraintErrorCodes {

  @Override
  public Map<String, ErrorCode> get() {
    return Map.of(
        "uk_playlist_subscriptions_playlist_subscriber", PlaylistErrorCode.SUBSCRIBE_DUPLICATE,
        "uk_playlist_contents_playlist_content", PlaylistErrorCode.PLAYLIST_CONTENT_DUPLICATE
    );
  }
}
