package com.codeit.mople.domain.playlist.dto.response;

import com.codeit.mople.global.dto.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistResponse(
    UUID id,
    UserSummary owner,
    String title,
    String description,
    Instant updatedAt,
    long subscriberCount,
    boolean subscribedByMe,
    List<PlaylistContentResponse> contents
) {

}
