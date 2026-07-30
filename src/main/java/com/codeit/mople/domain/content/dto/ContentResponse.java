package com.codeit.mople.domain.content.dto;

import java.util.List;
import java.util.UUID;

public record ContentResponse(
    UUID id,
    String type,
    String title,
    String description,
    String thumbnailUrl,
    List<String> tags,
    double averageRating,
    int reviewCount,
    long watcherCount
) {

}