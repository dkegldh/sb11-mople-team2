package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record PlaylistSubscribedEvent(
    UUID eventId,
    UUID ownerId,
    UUID playlistId,
    UUID subscriberId,
    String subscriberName,
    String playlistTitle
) implements PublishableEvent {

}
