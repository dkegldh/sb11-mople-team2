package com.codeit.mople.domain.content.client.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

// tmdb가 보낸것이 15개 이면 현재 필드에 있는거 말고는 무시해라
// tmdb가 poster_path(스네이크) 이런식으로 주는거를 그것에 맞게 마줘라
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbPageResponse<T>(
    int page,
    List<T> results,
    int totalPages,
    int totalResults
) {

}
