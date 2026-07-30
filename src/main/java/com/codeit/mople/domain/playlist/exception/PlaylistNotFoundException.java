package com.codeit.mople.domain.playlist.exception;

import java.util.Map;
import java.util.UUID;

public class PlaylistNotFoundException extends PlaylistException {

  public PlaylistNotFoundException(UUID playlistId) {
    super(PlaylistErrorCode.PLAYLIST_NOT_FOUND, Map.of("playlistId", playlistId));
  }
}
