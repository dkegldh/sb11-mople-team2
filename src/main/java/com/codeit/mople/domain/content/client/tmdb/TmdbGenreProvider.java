package com.codeit.mople.domain.content.client.tmdb;

import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreCatalog;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse.Genre;
import com.codeit.mople.global.config.CacheNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TmdbGenreProvider {

  private final TmdbClient tmdbClient;
  private final Counter loadFailureCounter;

  public TmdbGenreProvider(TmdbClient tmdbClient, MeterRegistry meterRegistry) {
    this.tmdbClient = tmdbClient;
    this.loadFailureCounter = Counter.builder("batch.tmdb.genre.load.failure")
        .description("TMDB 장르 조회 실패 횟수")
        .register(meterRegistry);
  }

  // 해당 메서드의 반환 값 캐싱(결과가 비어있으면 캐싱x)
  @Cacheable(cacheNames = CacheNames.TMDB_GENRES, key = "'catalog'", unless = "#result.isEmpty()")
  public TmdbGenreCatalog get() {
    Map<Integer, String> names = new HashMap<>();

    try {
      if (!putAll(names, tmdbClient.getMovieGenres()) || !putAll(names, tmdbClient.getTvGenres())) {
        loadFailureCounter.increment();
        log.error("TMDB 장르 목록이 비어 있습니다.");
        return TmdbGenreCatalog.empty();
      }
    } catch (Exception e) {
      loadFailureCounter.increment();   // 메트릭 카운터(장르 조회 실패횟수)
      log.error("TMDB 장르 조회에 실패했습니다.", e);
      return TmdbGenreCatalog.empty();
    }

    log.info("TMDB 장르 {}건을 조회했습니다.", names.size());
    return TmdbGenreCatalog.from(names);
  }

  // 비어있는 객체, Tmdb에 용청한 장르를 받음
  private boolean putAll(Map<Integer, String> target, TmdbGenreListResponse response) {
    // 응답 받은 장르가 비어있으면 false
    if (response == null || response.genres() == null) {
      return false;
    }

    // 응답 받은 장르 수 만큼 반복
    // 1. 중간에 장르가 비어있으면 건너띔, 2. 장르를 하나씩 비어있는 객체에 넣음
    int added = 0;
    for (Genre genre : response.genres()) {
      if (genre.id() == null || genre.name() == null || genre.name().isBlank()) {
        continue;
      }
      target.put(genre.id(), genre.name());
      added++;
    }
    return added > 0;
  }
}