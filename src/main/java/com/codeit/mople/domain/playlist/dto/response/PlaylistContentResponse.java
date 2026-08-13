package com.codeit.mople.domain.playlist.dto.response;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.playlist.entity.PlaylistContent;
import java.util.List;
import java.util.UUID;

public record PlaylistContentResponse(
    UUID id,
    String type,
    String title,
    String description,
    String thumbnailUrl,
    List<String> tags,
    double averageRating,
    int reviewCount
) {

  public static PlaylistContentResponse from(PlaylistContent playlistContent) {
    Content content = playlistContent.getContent();

    return new PlaylistContentResponse(
        content.getId(),
        content.getType().getValue(),
        content.getTitle(),
        content.getDescription(),
        content.getThumbnailUrl(),
        content.getTags(),
        content.calculateAverageRating(),
        content.getReviewCount()
    );
  }

}
