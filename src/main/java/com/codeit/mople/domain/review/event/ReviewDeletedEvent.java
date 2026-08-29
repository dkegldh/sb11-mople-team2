package com.codeit.mople.domain.review.event;

import com.codeit.mople.global.event.PublishableEvent;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record ReviewDeletedEvent(
    UUID eventId,
    @JsonFormat(shape = Shape.STRING)
    Instant occurredAt,
    UUID contentId,
    double rating
) implements PublishableEvent {

}
