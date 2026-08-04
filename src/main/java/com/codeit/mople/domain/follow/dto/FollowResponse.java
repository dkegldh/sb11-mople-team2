package com.codeit.mople.domain.follow.dto;

import com.codeit.mople.domain.follow.entity.Follow;
import java.util.UUID;

public record FollowResponse(
    UUID id,
    UUID followeeId,
    UUID followerId
) {
  public static FollowResponse from(Follow follow) {
    return new FollowResponse(
        follow.getId(),
        follow.getFollowee().getId(),
        follow.getFollower().getId()
    );
  }
}
