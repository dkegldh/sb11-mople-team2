package com.codeit.mople.domain.watchingsession.dto;

import com.codeit.mople.global.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record WatchingSessionResponse(
    UUID id,
    Instant createdAt,
    UserSummary watcher,
    WatchingSessionContentDto content
) {

}
