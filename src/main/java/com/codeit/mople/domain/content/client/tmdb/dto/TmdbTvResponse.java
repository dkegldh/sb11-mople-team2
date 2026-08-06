package com.codeit.mople.domain.content.client.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

// tmdb가 보낸것이 15개 이면 현재 필드에 있는거 5개 말고는 무시해라
// tmdb가 poster_path(스네이크) 이런식으로 주는거를 그것에 맞게 마줘라
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbTvResponse(
    Long id,                      // id
    String name,                  // 제목
    String overview,              // 영화 소개 줄거리
    String posterPath,            // 포스터 이미지 경로
    List<Integer> genreIds        // 장르 번호 예: [28, 12, 16, 10751]
) {

}
