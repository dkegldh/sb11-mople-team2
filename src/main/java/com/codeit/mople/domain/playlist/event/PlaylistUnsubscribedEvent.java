package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record PlaylistUnsubscribedEvent(
    UUID eventId,
    UUID playlistId,
    UUID subscriberId
) implements PublishableEvent {

}
