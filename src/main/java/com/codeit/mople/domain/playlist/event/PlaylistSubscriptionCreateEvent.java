package com.codeit.mople.domain.playlist.event;

import java.util.UUID;

public record PlaylistSubscriptionCreateEvent(
    UUID playlistId,
    UUID subscriberId
) {

}
