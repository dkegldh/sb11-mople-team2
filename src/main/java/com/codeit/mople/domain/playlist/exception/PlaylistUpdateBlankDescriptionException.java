package com.codeit.mople.domain.playlist.exception;


public class PlaylistUpdateBlankDescriptionException extends PlaylistException {

  public PlaylistUpdateBlankDescriptionException() {
    super(PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_DESCRIPTION);
  }
}
