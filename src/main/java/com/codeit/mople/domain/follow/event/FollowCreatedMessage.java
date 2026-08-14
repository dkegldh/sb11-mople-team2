package com.codeit.mople.domain.follow.event;

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
) {

  public static FollowCreatedMessage from(FollowCreatedEvent event) {
    return new FollowCreatedMessage(
        UUID.randomUUID(),
        Instant.now(),
        event.followId(),
        event.followeeId(),
        event.followerId(),
        event.followerName()
    );
  }
}