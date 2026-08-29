package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.global.event.PublishableEvent;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import java.time.Instant;
import java.util.UUID;

public record PlaylistContentAddedEvent(
    UUID eventId,
    @JsonFormat(shape = Shape.STRING)
    Instant occurredAt,
    UUID playlistContentId,
    UUID playlistId,
    UUID contentId,
    String playlistTitle
) implements PublishableEvent {

}