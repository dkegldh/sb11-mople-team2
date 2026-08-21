package com.codeit.mople.domain.playlist.event;

import java.time.Instant;
import java.util.UUID;

public record PlaylistContentAddedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID playlistContentId,
    UUID playlistId,
    UUID contentId,
    String playlistTitle
) {

}
