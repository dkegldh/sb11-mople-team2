package com.codeit.mople.domain.follow.event;

import java.util.UUID;

public record FollowDeletedEvent(
    UUID followId,
    UUID followeeId,
    UUID followerId
) {

}