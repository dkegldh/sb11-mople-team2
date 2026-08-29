package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.global.event.PublishableEvent;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record PlaylistSearchIndexEvent(
    UUID eventId,
    @JsonFormat(shape = Shape.STRING)
    Instant occurredAt,
    UUID playlistId,
    String title,
    Instant updatedAt,
    long subscribeCount
) implements PublishableEvent {

}