package com.codeit.mople.domain.playlist.mapper;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.playlist.dto.response.PlaylistContentResponse;
import com.codeit.mople.domain.playlist.entity.PlaylistContent;
import org.springframework.stereotype.Component;

@Component
public class PlaylistContentMapper {

  public PlaylistContentResponse toResponse(PlaylistContent playlistContent) {
    Content content = playlistContent.getContent();

    return new PlaylistContentResponse(
        content.getId(),
        content.getType().name(),
        content.getTitle(),
        content.getDescription(),
        content.getThumbnailUrl(),
        content.getTags(),
        content.getAverageRating(),
        content.getReviewCount()
    );
  }

}
