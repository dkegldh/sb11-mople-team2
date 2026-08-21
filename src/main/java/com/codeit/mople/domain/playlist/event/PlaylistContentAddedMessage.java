package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.global.event.PublishableEvent;
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
) implements PublishableEvent {

  public static PlaylistContentAddedMessage from(PlaylistContentAddedEvent event) {
    return new PlaylistContentAddedMessage(
        event.eventId(),
        event.occurredAt(),
        event.playlistContentId(),
        event.playlistId(),
        event.contentId(),
        event.playlistTitle()
    );
  }
}
