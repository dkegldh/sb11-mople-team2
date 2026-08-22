package com.codeit.mople.domain.content.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record ContentSearchIndexEvent(
    UUID eventId,
    UUID contentId,
    String title
) implements PublishableEvent {

}