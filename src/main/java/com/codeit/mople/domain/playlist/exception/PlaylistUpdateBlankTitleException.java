package com.codeit.mople.domain.playlist.exception;


public class PlaylistUpdateBlankTitleException extends PlaylistException {

  public PlaylistUpdateBlankTitleException() {
    super(PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_TITLE);
  }
}
