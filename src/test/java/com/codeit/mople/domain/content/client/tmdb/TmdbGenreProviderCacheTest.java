package com.codeit.mople.domain.content.client.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreCatalog;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse.Genre;
import com.codeit.mople.global.config.CacheNames;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("TMDB 장르 캐시 통합 테스트")
class TmdbGenreProviderCacheTest {

  private static final String CACHE_KEY = "catalog";

  @MockitoBean
  TmdbClient tmdbClient;

  @Autowired
  TmdbGenreProvider tmdbGenreProvider;

  @Autowired
  CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    clearGenreCache();
  }

  @AfterEach
  void tearDown() {
    clearGenreCache();
  }

  private void clearGenreCache() {
    cacheManager.getCache(CacheNames.TMDB_GENRES).clear();
  }

  private TmdbGenreListResponse genres(Genre... genres) {
    return new TmdbGenreListResponse(List.of(genres));
  }

  @Test
  @DisplayName("두 번 조회하면 TMDB 호출은 한 번만 나가는지")
  void cachesGenreCatalog() {
    // given
    given(tmdbClient.getMovieGenres()).willReturn(genres(new Genre(28, "액션")));
    given(tmdbClient.getTvGenres()).willReturn(genres(new Genre(16, "애니메이션")));

    // when
    TmdbGenreCatalog first = tmdbGenreProvider.get();
    TmdbGenreCatalog second = tmdbGenreProvider.get();

    // then
    assertThat(first.toMap()).containsExactlyInAnyOrderEntriesOf(second.toMap());
    assertThat(second.toMap()).containsEntry(28, "액션").containsEntry(16, "애니메이션");
    verify(tmdbClient, times(1)).getMovieGenres();
    verify(tmdbClient, times(1)).getTvGenres();
  }

  @Test
  @DisplayName("TMDB 호출이 실패하면 예외를 전파하고 캐싱하지 않는지")
  void propagatesAndDoesNotCacheWhenCallFails() {
    // given
    IllegalStateException cause = new IllegalStateException("tmdb down");
    given(tmdbClient.getMovieGenres()).willThrow(cause);

    // when, then
    assertThatThrownBy(() -> tmdbGenreProvider.get())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TMDB 장르 조회에 실패했습니다.")
        .hasCause(cause);
    assertThatThrownBy(() -> tmdbGenreProvider.get())
        .isInstanceOf(IllegalStateException.class);

    assertThat(cacheManager.getCache(CacheNames.TMDB_GENRES).get(CACHE_KEY)).isNull();
    verify(tmdbClient, times(2)).getMovieGenres();
  }

  @Test
  @DisplayName("TMDB 응답의 장르 목록이 비어 있으면 예외를 전파하고 캐싱하지 않는지")
  void propagatesAndDoesNotCacheWhenResponseIsEmpty() {
    // given
    given(tmdbClient.getMovieGenres()).willReturn(genres());
    given(tmdbClient.getTvGenres()).willReturn(genres(new Genre(16, "애니메이션")));

    // when, then
    assertThatThrownBy(() -> tmdbGenreProvider.get())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TMDB 장르 목록이 비어 있습니다.");

    assertThat(cacheManager.getCache(CacheNames.TMDB_GENRES).get(CACHE_KEY)).isNull();
  }
}