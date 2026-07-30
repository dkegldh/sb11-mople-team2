package com.codeit.mople.domain.playlist.exception;

import java.util.Map;
import java.util.UUID;

public class PlaylistForbiddenException extends PlaylistException {

  public PlaylistForbiddenException(UUID playlistId) {
    super(PlaylistErrorCode.PLAYLIST_FORBIDDEN, Map.of("playlistId", playlistId));
  }
}
