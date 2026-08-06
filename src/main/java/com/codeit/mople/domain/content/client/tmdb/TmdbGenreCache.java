package com.codeit.mople.domain.content.client.tmdb;

import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// TMDB 장르는 고정 데이터라 메모리에 저장
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbGenreCache {

  private static final Duration RETRY_INTERVAL = Duration.ofMinutes(5);

  private final TmdbClient tmdbClient;

  // cpu 캐시에 사본을 두지 말고 매번 메인 메모리에서 읽고 써라(휘발성)
  private volatile Map<Integer, String> genreNames = Map.of();

  // 마지막으로 적재 시도한 시각
  private final AtomicLong lastAttemptedAt = new AtomicLong(0L);

  // 앱이 실행되면 리스너 동작
  @EventListener(ApplicationReadyEvent.class)
  public void loadOnStartup() {
    refresh();
  }


  public void refresh() {
    lastAttemptedAt.set(System.currentTimeMillis());
    load();
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

  public String getName(Integer genreId) {
    Map<Integer, String> current = currentGenres();
    return current.isEmpty() ? null : resolve(current, genreId);
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

  private void load() {
    try {
      Map<Integer, String> loaded = new HashMap<>();
      putAll(loaded, tmdbClient.getMovieGenres());
      putAll(loaded, tmdbClient.getTvGenres());
      genreNames = Map.copyOf(loaded);
      log.info("TMDB 장르 캐시 적재 완료: {}건", genreNames.size());
    } catch (Exception e) {
      log.warn("TMDB 장르 캐시 적재 실패, 최대 {}분간 태크 없이 진행", RETRY_INTERVAL.toMinutes(), e);
    }
  }

  //
  private String resolve(Map<Integer, String> genres, Integer genreId) {
    String name = genres.get(genreId);
    if (name == null) {
      log.warn("알 수 없는 TMDB 장르 id: {}", genreId);
    }
    return name;
  }

  // 1. 비어있는 hashMap, 장르list <id, name> 를 받음
  // 2. 요청해서 받은 장르 list가 비어있으면 리턴함
  // 3. 장르list 값이 채워져 있으면 그것을 target Map에 넣음
  private void putAll(Map<Integer, String> target, TmdbGenreListResponse response) {
    if (response == null || response.genres() == null) {
      return;
    }
    response.genres().forEach(genre -> target.put(genre.id(), genre.name()));
  }
}
