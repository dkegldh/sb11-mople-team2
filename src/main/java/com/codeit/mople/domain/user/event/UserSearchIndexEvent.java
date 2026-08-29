package com.codeit.mople.domain.user.event;

import com.codeit.mople.global.event.PublishableEvent;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record UserSearchIndexEvent(
    UUID eventId,
    @JsonFormat(shape = Shape.STRING)
    Instant occurredAt,
    UUID userId,
    String email,
    String name,
    Instant createdAt,
    boolean locked,
    String role
) implements PublishableEvent {
}