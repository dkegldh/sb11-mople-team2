package com.codeit.mople.domain.content.client.tmdb.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.BDDAssertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.codeit.mople.domain.content.client.tmdb.TmdbClient;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreListResponse.Genre;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbMovieResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbPageResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbTvResponse;
import com.codeit.mople.domain.content.repository.ContentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@DisplayName("TMDB 수집 job 통합 테스트")
public class TmdbCollectJobConfigTest {

  private static final String COLLECTE_DATE = "2026-08-05";

  @Autowired
  private JobLauncherTestUtils jobLauncherTestUtils;

  @Autowired
  private JobRepositoryTestUtils jobRepositoryTestUtils;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private MeterRegistry meterRegistry;

  @Autowired
  @Qualifier("tmdbCollectJob")
  private Job tmdbCollectJob;

  @MockitoBean
  private TmdbClient tmdbClient;

  @BeforeEach
  void setUp() {
    jobLauncherTestUtils.setJob(tmdbCollectJob);
    jobRepositoryTestUtils.removeJobExecutions();
    contentRepository.deleteAll();

    given(tmdbClient.getMovieGenres()).willReturn(genreList(28, "액션"));
    given(tmdbClient.getTvGenres()).willReturn(genreList(10759, "액션 & 어드벤처"));
    given(tmdbClient.getPopularMovies(anyInt())).willReturn(movePage());
    given(tmdbClient.getPopularTvSeries(anyInt())).willReturn(tvPage());
  }

  @Nested
  @DisplayName("Job 실행")
  class Launch {

    @Test
    @DisplayName("장르, 영화 Step, TV Step이 순서대로 돌고 수집분이 저장됨.")
    void launchJob_CompletesAndSaves() throws Exception {
      JobParameters parameters = jobParameters(COLLECTE_DATE);

      JobExecution execution = jobLauncherTestUtils.launchJob(parameters);

      assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
      assertThat(contentRepository.count()).isEqualTo(4);

      assertThat(execution.getStepExecutions())
          .extracting(StepExecution::getStepName, StepExecution::getReadCount)
          .containsExactly(
              tuple("tmdbGenreCheckStep", 0L),
              tuple("tmdbMovieStep", 2L),
              tuple("tmdbTvStep", 2L));
    }

    @Test
    @DisplayName("성공하면 batch.tmdb.success 카운터가 1 증가")
    void launchJob_IncrementsSuccessCounter() throws Exception {
      double before = meterRegistry.get("batch.tmdb.success").counter().count();

      jobLauncherTestUtils.launchJob(jobParameters(COLLECTE_DATE));

      assertThat(meterRegistry.get("batch.tmdb.success").counter().count())
          .isEqualTo(before + 1);
    }
  }

  @Nested
  @DisplayName("하루 1회 멱등성")
  class Idemptency {

    @Test
    @DisplayName("같은 수집일자로 재실항하면 JobInstanceAlreadyCompleteException이 남")
    void lauchJob_SameCollectDate_AlreadyComplete() throws Exception {
      jobLauncherTestUtils.launchJob(jobParameters(COLLECTE_DATE));

      assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParameters((COLLECTE_DATE))))
          .isInstanceOf(JobInstanceAlreadyCompleteException.class);
      assertThat(contentRepository.count()).isEqualTo(4);
    }
  }

  private JobParameters jobParameters(String collectDate) {
    return new JobParametersBuilder()
        .addString("collectDate", collectDate)
        .addLong("maxPages", 1L, false)
        .toJobParameters();
  }

  private TmdbPageResponse<TmdbMovieResponse> movePage() {
    return new TmdbPageResponse<>(
        1,
        List.of(
            new TmdbMovieResponse(1L, "듄", "줄거리", "/dune.jpg", List.of(28)),
            new TmdbMovieResponse(2L, "오펜하이머", "줄거리", null, List.of())),
        1,
        2);
  }

  private TmdbPageResponse<TmdbTvResponse> tvPage() {
    return new TmdbPageResponse<>(
        1,
        List.of(
            new TmdbTvResponse(1L, "김부장", "줄거리", "/kim.jpg", List.of(10759)),
            new TmdbTvResponse(2L, "킬러들의 쇼핑몰", "줄거리", null, List.of())),
        1,
        2);
  }

  private TmdbGenreListResponse genreList(int id, String name) {
    return new TmdbGenreListResponse(List.of(new Genre(id, name)));
  }

}
