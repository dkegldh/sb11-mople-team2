package com.codeit.mople.domain.review.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record ReviewUpdatedEvent(
    UUID eventId,
    UUID contentId,
    double oldRating,
    double newRating
) implements PublishableEvent {

}
