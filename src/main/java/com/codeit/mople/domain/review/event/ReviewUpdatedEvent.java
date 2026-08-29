package com.codeit.mople.domain.review.event;

import com.codeit.mople.global.event.PublishableEvent;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record ReviewUpdatedEvent(
    UUID eventId,
    @JsonFormat(shape = Shape.STRING)
    Instant occurredAt,
    UUID contentId,
    double oldRating,
    double newRating
) implements PublishableEvent {

}
