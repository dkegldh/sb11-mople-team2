package com.codeit.mople.domain.content.client.tmdb;

import com.codeit.mople.domain.content.client.tmdb.config.TmdbFeignConfig;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbMovieResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbPageResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbTvResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// tmdb에 요청하고 응답 json을 dto로 받아서 반환까지
@FeignClient(
    name = "tmdb",
    url = "${external-api.tmdb.base-url}",
    configuration = TmdbFeignConfig.class)
public interface TmdbClient {

  @GetMapping("/movie/popular")
  TmdbPageResponse<TmdbMovieResponse> getPopularMovies(@RequestParam("page") int page);

  @GetMapping("/tv/popular")
  TmdbPageResponse<TmdbTvResponse> getPopularTvSeries(@RequestParam("page") int page);

  @GetMapping("/genre/movie/list")
  TmdbGenreListResponse getMovieGenres();

  @GetMapping("/genre/tv/list")
  TmdbGenreListResponse getTvGenres();

}
