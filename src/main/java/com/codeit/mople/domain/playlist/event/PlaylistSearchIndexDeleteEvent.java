package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record PlaylistSearchIndexDeleteEvent(
    UUID eventId,
    UUID playlistId
) implements PublishableEvent {

}
