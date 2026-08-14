package com.codeit.mople.domain.playlist.event;

import java.util.UUID;

public record PlaylistUnsubscribedEvent(
    UUID eventId,
    UUID playlistId,
    UUID subscriberId
) {

}
