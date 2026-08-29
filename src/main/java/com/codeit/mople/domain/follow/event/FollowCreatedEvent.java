package com.codeit.mople.domain.follow.event;

import com.codeit.mople.global.event.PublishableEvent;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import java.time.Instant;
import java.util.UUID;

public record FollowCreatedEvent(
    UUID eventId,
    @JsonFormat(shape = Shape.STRING)
    Instant occurredAt,
    UUID followId,
    UUID followeeId,
    UUID followerId,
    String followerName
) implements PublishableEvent {

}