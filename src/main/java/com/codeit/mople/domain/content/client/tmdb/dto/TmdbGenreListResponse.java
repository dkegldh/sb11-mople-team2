package com.codeit.mople.domain.content.client.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// tmdb가 보낸것이 15개 이면 현재 필드에 있는거 5개 말고는 무시해라
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbGenreListResponse(
    List<Genre> genres
) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Genre(
      Integer id,
      String name
  ) {

  }
}

