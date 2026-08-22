package com.codeit.mople.domain.content.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record ContentSearchIndexDeleteEvent(
    UUID eventId,
    UUID contentId
) implements PublishableEvent {

}