package com.codeit.mople.domain.playlist.event;

import java.util.UUID;

public record PlaylistCreatedEvent(
    UUID ownerId,
    String ownerName,
    String playlistTitle
) {

}
