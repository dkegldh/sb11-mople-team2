package com.codeit.mople.domain.content.client.tmdb;

import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse.Genre;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// TMDB 장르는 고정 데이터라 메모리에 저장
@Slf4j
@Component
public class TmdbGenreCache {

  private static final Duration RETRY_INTERVAL = Duration.ofMinutes(5);

  private final TmdbClient tmdbClient;

  // cpu 캐시에 사본을 두지 말고 매번 메인 메모리에서 읽고 써라(휘발성)
  private volatile Map<Integer, String> genreNames = Map.of();

  // 마지막 갱신 시도가 실패했는지
  private volatile boolean lastRefreshFailed = false;

  // 마지막으로 적재 시도한 시각
  private final AtomicLong lastAttemptedAt = new AtomicLong(0L);

  // 캐시에 없던 장르 id
  private final Set<Integer> unknownGenreIds = ConcurrentHashMap.newKeySet();

  public TmdbGenreCache(TmdbClient tmdbClient, MeterRegistry meterRegistry) {
    this.tmdbClient = tmdbClient;

    Gauge.builder("tmdb.genre.cache.available", this, cache -> cache.genreNames.isEmpty() ? 0 : 1)
        .description("TMDB 장르 캐시 사용 가능 여부(1 = 태그를 붙일 수 있는 상태)")
        .register(meterRegistry);
    Gauge.builder("tmdb.genre.cache.stale", this, cache -> cache.lastRefreshFailed ? 1 : 0)
        .description("TMDB 장르 갱신 실패 여부(1 = 마지막 갱신이 실패한 상태)")
        .register(meterRegistry);
    Gauge.builder("tmdb.genre.cache.size", this, cache -> cache.genreNames.size())
        .description("TMDB 장르 캐시에 적재된 장르 수")
        .register(meterRegistry);
  }

  // 앱이 실행되면 리스너 동작
  @EventListener(ApplicationReadyEvent.class)
  public void loadOnStartup() {
    refresh();
  }

  // 적재 시도하고 성공 여부를 돌려줌
  public boolean refresh() {
    lastAttemptedAt.set(System.currentTimeMillis());
    return load();
  }

  // true이면 캐시에 장르가 남아있어서 태그를 붙일 수 있는상태
  public boolean isAvailable() {
    return !genreNames.isEmpty();
  }

  public List<String> getNames(List<Integer> genreIds) {
    if (genreIds == null || genreIds.isEmpty()) {
      return List.of();
    }

    Map<Integer, String> current = currentGenres();
    if (current.isEmpty()) {
      return List.of();
    }

    return genreIds.stream()
        .map(id -> resolve(current, id))
        .filter(Objects::nonNull)
        .toList();
  }

  // genreNames가 비어있으면 다시 채워넣는 메서드
  private Map<Integer, String> currentGenres() {
    if (genreNames.isEmpty() && tryAcquireAttempt()) {
      load();
    }
    return genreNames;
  }

  private boolean tryAcquireAttempt() {
    long now = System.currentTimeMillis();
    long last = lastAttemptedAt.get();

    if (now - last < RETRY_INTERVAL.toMillis()) {
      return false;
    }
    return lastAttemptedAt.compareAndSet(last, now);
  }

  // 영화,Tv 중 하나라도 실패하면 기존 캐시를 유지
  private boolean load() {
    logUnknownGenreIds();

    Map<Integer, String> loading = new HashMap<>();
    try {
      if (!putAll(loading, tmdbClient.getMovieGenres()) || !putAll(loading, tmdbClient.getTvGenres())) {
        lastRefreshFailed = true;
        log.error("TMDB 장르 목록이 비어 적재하지 않았습니다. 기존 캐시 {}건 유지", genreNames.size());
        return false;
      }
    } catch (Exception e) {
      lastRefreshFailed = true;
      log.error("TMDB 장르 캐시 적재 실패. 기존 캐시 {}건 유지, 최대 {}분간 재시도 없음",
          genreNames.size(), RETRY_INTERVAL.toMinutes(), e);
      return false;
    }
    genreNames = Map.copyOf(loading);
    lastRefreshFailed = false;
    log.info("TMDB 장르 캐시 적재 완료: {}건", genreNames.size());
    return true;
  }

  // 캐시에 없는 id만 모아둠 (로그는 logUnknownGenreIds에서 한 번에)
  private String resolve(Map<Integer, String> genres, Integer genreId) {
    if (genreId == null) {
      return null;
    }

    String name = genres.get(genreId);
    if (name == null) {
      unknownGenreIds.add(genreId);
    }
    return name;
  }

  private void logUnknownGenreIds() {
    if (unknownGenreIds.isEmpty()) {
      return;
    }

    List<Integer> unknown = List.copyOf(unknownGenreIds);
    unknown.forEach(unknownGenreIds::remove);
    log.warn("캐시에 없는 TMDB 장르 id {}종: {} (캐시 {}건, 마지막 갱신 실패={})",
        unknown.size(), unknown, genreNames.size(), lastRefreshFailed);
  }

  // 한 것도 못 담으면 실패로 처리함
  private boolean putAll(Map<Integer, String> target, TmdbGenreListResponse response) {
    if (response == null || response.genres() == null) {
      return false;
    }

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
