package com.codeit.mople.domain.playlist.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record PlaylistSearchIndexEvent(
    UUID eventId,
    UUID playlistId,
    String title
) implements PublishableEvent {

}