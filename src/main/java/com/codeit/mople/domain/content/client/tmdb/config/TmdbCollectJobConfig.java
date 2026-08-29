package com.codeit.mople.domain.content.client.tmdb.config;


import com.codeit.mople.domain.content.client.tmdb.TmdbClient;
import com.codeit.mople.domain.content.client.tmdb.TmdbGenreProvider;
import com.codeit.mople.domain.content.client.tmdb.batch.TmdbContentItemProcessor;
import com.codeit.mople.domain.content.client.tmdb.batch.TmdbContentItemWriter;
import com.codeit.mople.domain.content.client.tmdb.batch.TmdbGenreCatalogHolder;
import com.codeit.mople.domain.content.client.tmdb.batch.TmdbPageItemReader;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbContentItem;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreCatalog;
import com.codeit.mople.domain.content.client.tmdb.listener.TmdbCollectJobListener;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TmdbCollectJobConfig {

  private static final int CHUNK_SIZE = TmdbPageItemReader.PAGE_SIZE;

  private final TmdbClient tmdbClient;
  private final TmdbGenreProvider tmdbGenreProvider;
  private final TmdbProperties tmdbProperties;
  private final ContentRepository contentRepository;
  private final MeterRegistry meterRegistry;

  @Bean
  public Job tmdbCollectJob(
      JobRepository jobRepository,
      TmdbCollectJobListener listener,
      @Qualifier("tmdbGenreCheckStep") Step tmdbGenreCheckStep,
      @Qualifier("tmdbMovieStep") Step tmdbMovieStep,
      @Qualifier("tmdbTvStep") Step tmdbTvStep
  ) {
    return new JobBuilder("tmdbCollectJob", jobRepository)
        .listener(listener)
        .start(tmdbGenreCheckStep)
        .next(tmdbMovieStep)
        .next(tmdbTvStep)
        .build();
  }

  @Bean
  public TmdbCollectJobListener tmdbCollectJobListener() {
    return new TmdbCollectJobListener(meterRegistry);
  }

  // Job 실행당 한 번만 해석되어 게이트 Step과 두 프로세서가 같은것을 공유
  @Bean
  @JobScope
  public TmdbGenreCatalogHolder tmdbGenreCatalogHolder() {
    TmdbGenreCatalog catalog = tmdbGenreProvider.get();

    if (catalog.isEmpty()) {
      throw new IllegalStateException(
          "TMDB 장르 카탈로그가 비어 있어 수집을 중단합니다. 태그 없는 콘텐츠 저장을 막습니다.");
    }
    return new TmdbGenreCatalogHolder(catalog);
  }

  @Bean
  public Step tmdbGenreCheckStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      TmdbGenreCatalogHolder tmdbGenreCatalogHolder) {
    return new StepBuilder("tmdbGenreCheckStep", jobRepository)
        .tasklet((contribution, chunkContext) -> {
          log.info("TMDB 장르 카탈로그 {}건을 확인했습니다.", tmdbGenreCatalogHolder.size());
          return RepeatStatus.FINISHED;
        }, transactionManager)
        .allowStartIfComplete(true)
        .build();
  }

  @Bean
  public Step tmdbMovieStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      @Qualifier("tmdbMovieReader") TmdbPageItemReader tmdbMovieReader,
      @Qualifier("tmdbMovieProcessor") TmdbContentItemProcessor tmdbMovieProcessor,
      @Value("${batch.tmdb.collect.skip-limit}") int skipLimit) {
    return new StepBuilder("tmdbMovieStep", jobRepository)
        .<TmdbContentItem, Content>chunk(CHUNK_SIZE, transactionManager)
        .reader(tmdbMovieReader)
        .processor(tmdbMovieProcessor)
        .writer(writer(ContentType.MOVIE))
        .faultTolerant()
        .skip(DataIntegrityViolationException.class)
        .skipLimit(skipLimit)
        .build();
  }

  @Bean
  public Step tmdbTvStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      @Qualifier("tmdbTvReader") TmdbPageItemReader tmdbTvReader,
      @Qualifier("tmdbTvProcessor") TmdbContentItemProcessor tmdbTvProcessor,
      @Value("${batch.tmdb.collect.skip-limit}") int skipLimit) {
    return new StepBuilder("tmdbTvStep", jobRepository)
        .<TmdbContentItem, Content>chunk(CHUNK_SIZE, transactionManager)
        .reader(tmdbTvReader)
        .processor(tmdbTvProcessor)
        .writer(writer(ContentType.TV_SERIES))
        .faultTolerant()
        .skip(DataIntegrityViolationException.class)
        .skipLimit(skipLimit)
        .build();
  }

  @Bean
  @StepScope
  public TmdbPageItemReader tmdbMovieReader(
      @Value("#{jobParameters['maxPages'] ?: ${batch.tmdb.collect.max-pages}}") int maxPages) {
    return new TmdbPageItemReader("tmdbMovieReader", tmdbClient::getPopularMovies, maxPages);
  }

  @Bean
  @StepScope
  public TmdbPageItemReader tmdbTvReader(
      @Value("#{jobParameters['maxPages'] ?: ${batch.tmdb.collect.max-pages}}") int maxPages) {
    return new TmdbPageItemReader("tmdbTvReader", tmdbClient::getPopularTvSeries, maxPages);
  }

  @Bean
  @StepScope
  public TmdbContentItemProcessor tmdbMovieProcessor(TmdbGenreCatalogHolder holder) {
    return processor(ContentType.MOVIE, holder);
  }

  @Bean
  @StepScope
  public TmdbContentItemProcessor tmdbTvProcessor(TmdbGenreCatalogHolder holder) {
    return processor(ContentType.TV_SERIES, holder);
  }

  private TmdbContentItemProcessor processor(
      ContentType contentType, TmdbGenreCatalogHolder holder) {
    return new TmdbContentItemProcessor(contentType, holder.names(), tmdbProperties);
  }

  private ItemWriter<Content> writer(ContentType contentType) {
    return new TmdbContentItemWriter(contentRepository, contentType, meterRegistry);
  }
}
