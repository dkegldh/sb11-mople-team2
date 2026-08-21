package com.codeit.mople.domain.follow.event;

import java.time.Instant;
import java.util.UUID;

public record FollowCreatedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID followId,
    UUID followeeId,
    UUID followerId,
    String followerName
) {

}
