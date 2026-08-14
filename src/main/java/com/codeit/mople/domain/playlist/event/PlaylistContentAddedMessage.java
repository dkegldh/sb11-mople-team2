package com.codeit.mople.domain.playlist.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import java.time.Instant;
import java.util.UUID;

public record PlaylistContentAddedMessage(
    UUID eventId,
    @JsonFormat(shape = Shape.STRING)
    Instant occurredAt,
    UUID playlistContentId,
    UUID playlistId,
    UUID contentId,
    String playlistTitle
) {

  public static PlaylistContentAddedMessage from(PlaylistContentAddedEvent event) {
    return new PlaylistContentAddedMessage(
        UUID.randomUUID(),
        Instant.now(),
        event.playlistContentId(),
        event.playlistId(),
        event.contentId(),
        event.playlistTitle()
    );
  }
}
