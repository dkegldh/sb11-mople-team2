package com.codeit.mople.domain.content.client.tmdb.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.codeit.mople.domain.content.client.tmdb.TmdbClient;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.global.config.CacheNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@DisplayName("TMDB 장르 카탈로그가 비었을 때 수집 배치 통합 테스트")
class TmdbCollectJobEmptyGenreTest {

  @Autowired
  private JobLauncher jobLauncher;

  @Autowired
  @Qualifier("tmdbCollectJob")
  private Job tmdbCollectJob;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private CacheManager cacheManager;

  @MockitoBean
  private TmdbClient tmdbClient;

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

  @Test
  @DisplayName("장르 조회가 실패하면 게이트 Step에서 멈추고 수집 Step은 실행되지 않는지")
  void run_WhenGenreCatalogEmpty_StopsAtGenreCheckStep() throws Exception {
    // given
    given(tmdbClient.getMovieGenres()).willThrow(new IllegalStateException("tmdb down"));
    long savedBefore = contentRepository.count();

    JobParameters parameters = new JobParametersBuilder()
        .addLong("runId", System.nanoTime(), true)
        .toJobParameters();

    // when
    JobExecution execution = jobLauncher.run(tmdbCollectJob, parameters);

    // then
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(execution.getStepExecutions())
        .singleElement()
        .satisfies(step -> {
          assertThat(step.getStepName()).isEqualTo("tmdbGenreCheckStep");
          assertThat(step.getStatus()).isEqualTo(BatchStatus.FAILED);
        });
    assertThat(contentRepository.count()).isEqualTo(savedBefore);
  }
}