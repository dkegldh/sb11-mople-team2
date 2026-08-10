package com.codeit.mople.domain.content.client.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse.Genre;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TmdbGenreCache 단위 테스트")
public class TmdbGenreCacheTest {

  @Mock
  private TmdbClient tmdbClient;

  @InjectMocks
  private TmdbGenreCache genreCache;

  @Nested
  @DisplayName("장르 적재 성공")
  class LoadSuccess {

    @Test
    @DisplayName("refresh 1회에 TMDB 호출은 영화,TV 각 1회만 한다")
    void refresh_CallsEachEndpointOnce() {
      given(tmdbClient.getMovieGenres()).willReturn(genres(28, "액션"));
      given(tmdbClient.getTvGenres()).willReturn(genres(10759, "액션 & 어드벤처"));

      genreCache.refresh();

      verify(tmdbClient, times(1)).getMovieGenres();
      verify(tmdbClient, times(1)).getTvGenres();
      assertThat(genreCache.getNames(List.of(28, 10759)))
          .containsExactly("액션", "액션 & 어드벤처");
    }

    @Test
    @DisplayName("적재된 뒤에는 조회를 반복해도 TMDB를 다시 호출하지 않음")
    void getNames_AfterLoad_NoAdditionalCall() {
      given(tmdbClient.getMovieGenres()).willReturn(genres(28, "액션"));
      given(tmdbClient.getTvGenres()).willReturn(genres(10759, "액션 & 어드벤처"));
      genreCache.refresh();

      for (int i = 0; i < 20; i++) {
        genreCache.getNames(List.of(28));
      }

      verify(tmdbClient, times(1)).getMovieGenres();
    }
  }

  @Nested
  @DisplayName("장르 적재 실패")
  class LoadFailure {

    @Test
    @DisplayName("적재 실패 후 조회를 20번 반복해도 재시도가 폭주하지 않음")
    void getNames_AfterFailure_RetryThrottled() {
      given(tmdbClient.getMovieGenres()).willThrow(new IllegalStateException("TMDB 장애"));
      genreCache.refresh();

      for (int i = 0; i < 20; i++) {
        genreCache.getNames(List.of(28, 12, 16));
      }

      verify(tmdbClient, times(1)).getMovieGenres();
    }

    @Test
    @DisplayName("적재 실패 상태의 조회는 예외 없이 빈 태그를 돌려줌")
    void getNames_AfterFailure_ReturnsEmpty() {
      given(tmdbClient.getMovieGenres()).willThrow(new IllegalStateException("TMDB 장애"));
      genreCache.refresh();

      List<String> names = genreCache.getNames(List.of(28, 12));

      assertThat(names).isEmpty();
      verify(tmdbClient, never()).getTvGenres();
    }
  }

  private TmdbGenreListResponse genres(int id, String name) {
    return new TmdbGenreListResponse(List.of(new Genre(id, name)));
  }
}
