package com.codeit.mople.domain.content.event;

import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.global.event.PublishableEvent;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record ContentSearchIndexEvent(
    UUID eventId,
    @JsonFormat(shape = Shape.STRING)
    Instant occurredAt,
    UUID contentId,
    String title,
    ContentType type,
    double rating,
    long watcherCount,
    Instant createdAt
) implements PublishableEvent {
}