package com.codeit.mople.domain.watchingsession.dto;

import java.util.List;
import java.util.UUID;

public record WatchingSessionContentDto(
    UUID id,
    String type,
    String title,
    String description,
    String thumbnailUrl,
    List<String> tags,
    double averageRating,
    int reviewCount
) {
}