package com.codeit.mople.domain.playlist.event;

import java.util.UUID;

public record PlaylistSubscribedEvent(
    UUID eventId,
    UUID ownerId,
    UUID playlistId,
    UUID subscriberId,
    String subscriberName,
    String playlistTitle
) {

}
