package com.codeit.mople.domain.review.event;

import java.util.UUID;

public record ReviewCreatedEvent(
    UUID eventId,
    UUID contentId,
    double rating
) {

}
