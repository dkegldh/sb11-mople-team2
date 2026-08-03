package com.codeit.mople.domain.playlist.event;

import java.util.UUID;

public record PlaylistSubscribedEvent(
    UUID ownerId,
    UUID playlistId,
    UUID subscriberId
) {

}
