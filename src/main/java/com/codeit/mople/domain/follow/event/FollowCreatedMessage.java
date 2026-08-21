package com.codeit.mople.domain.follow.event;

import com.codeit.mople.global.event.PublishableEvent;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record FollowCreatedMessage(
    UUID eventId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant occurredAt,
    UUID followId,
    UUID followeeId,
    UUID followerId,
    String followerName
) implements PublishableEvent {

  public static FollowCreatedMessage from(FollowCreatedEvent event) {
    return new FollowCreatedMessage(
        event.eventId(),
        event.occurredAt(),
        event.followId(),
        event.followeeId(),
        event.followerId(),
        event.followerName()
    );
  }
}