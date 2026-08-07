package com.codeit.mople.domain.follow.event;

import java.util.UUID;

public record FolloweeActivityEvent(
    UUID followeeId,
    String followeeName,
    String activityDescription,
    UUID followerId
) {

}
