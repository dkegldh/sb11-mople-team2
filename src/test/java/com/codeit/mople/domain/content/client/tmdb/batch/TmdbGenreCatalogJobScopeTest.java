package com.codeit.mople.domain.content.client.tmdb.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.content.client.tmdb.TmdbClient;
import com.codeit.mople.domain.content.client.tmdb.TmdbGenreProvider;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreCatalog;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbMovieResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbPageResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbTvResponse;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.global.config.CacheNames;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@SpringBatchTest
@DisplayName("TMDB 장르 카탈로그 Job 스코프 통합 테스트")
class TmdbGenreCatalogJobScopeTest {

  @Autowired
  private JobLauncherTestUtils jobLauncherTestUtils;

  @Autowired
  private JobRepositoryTestUtils jobRepositoryTestUtils;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private CacheManager cacheManager;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  @Qualifier("tmdbCollectJob")
  private Job tmdbCollectJob;

  @MockitoBean
  private TmdbClient tmdbClient;

  @MockitoSpyBean
  private TmdbGenreProvider tmdbGenreProvider;

  @BeforeEach
  void setUp() {
    jobLauncherTestUtils.setJob(tmdbCollectJob);
    jobRepositoryTestUtils.removeJobExecutions();
    contentRepository.deleteAll();
    cacheManager.getCache(CacheNames.TMDB_GENRES).clear();

    given(tmdbClient.getPopularMovies(anyInt())).willReturn(moviePage());
    given(tmdbClient.getPopularTvSeries(anyInt())).willReturn(tvPage());
  }

  @Test
  @DisplayName("게이트 통과 뒤 장르 조회가 실패해도 프로세서가 재조회하지 않아 태그와 함께 수집이 끝나는지")
  void launchJob_ResolvesGenreCatalogOncePerJob() throws Exception {
    // given
    willReturn(catalog())
        .willThrow(new IllegalStateException("TMDB 장르 조회에 실패했습니다."))
        .given(tmdbGenreProvider).get();

    // when
    JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters());

    // then
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    verify(tmdbGenreProvider, times(1)).get();
    assertThat(collectedTags()).contains(List.of("액션"), List.of("액션 & 어드벤처"));
  }

  private List<List<String>> collectedTags() {
    return transactionTemplate.execute(status -> contentRepository.findAll().stream()
        .map(content -> List.copyOf(content.getTags()))
        .toList());
  }

  private TmdbGenreCatalog catalog() {
    return TmdbGenreCatalog.from(Map.of(28, "액션", 10759, "액션 & 어드벤처"));
  }

  private JobParameters jobParameters() {
    return new JobParametersBuilder()
        .addString("collectDate", "2026-08-27")
        .addLong("maxPages", 1L, false)
        .toJobParameters();
  }

  private TmdbPageResponse<TmdbMovieResponse> moviePage() {
    return new TmdbPageResponse<>(
        1,
        List.of(new TmdbMovieResponse(1L, "듄", "줄거리", "/dune.jpg", List.of(28))),
        1,
        1);
  }

  private TmdbPageResponse<TmdbTvResponse> tvPage() {
    return new TmdbPageResponse<>(
        1,
        List.of(new TmdbTvResponse(1L, "김부장", "줄거리", "/kim.jpg", List.of(10759))),
        1,
        1);
  }
}