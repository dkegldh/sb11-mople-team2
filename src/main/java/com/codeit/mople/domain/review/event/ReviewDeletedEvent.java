package com.codeit.mople.domain.review.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record ReviewDeletedEvent(
    UUID eventId,
    UUID contentId,
    double rating
) implements PublishableEvent {

}
