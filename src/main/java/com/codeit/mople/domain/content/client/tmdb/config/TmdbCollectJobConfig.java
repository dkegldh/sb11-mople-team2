package com.codeit.mople.domain.content.client.tmdb.config;


import com.codeit.mople.domain.content.client.tmdb.TmdbClient;
import com.codeit.mople.domain.content.client.tmdb.TmdbGenreCache;
import com.codeit.mople.domain.content.client.tmdb.batch.TmdbContentItemProcessor;
import com.codeit.mople.domain.content.client.tmdb.batch.TmdbContentItemWriter;
import com.codeit.mople.domain.content.client.tmdb.batch.TmdbPageItemReader;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbContentItem;
import com.codeit.mople.domain.content.client.tmdb.listener.TmdbCollectJobListener;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class TmdbCollectJobConfig {

  private static final int CHUNK_SIZE = TmdbPageItemReader.PAGE_SIZE;

  private final TmdbClient tmdbClient;
  private final TmdbGenreCache tmdbGenreCache;
  private final TmdbProperties tmdbProperties;
  private final ContentRepository contentRepository;
  private final MeterRegistry meterRegistry;

  @Bean
  public Job tmdbCollectJob(
      JobRepository jobRepository,
      TmdbCollectJobListener listener,
      @Qualifier("tmdbMovieStep") Step tmdbMovieStep,
      @Qualifier("tmdbTvStep") Step tmdbTvStep
  ) {
    return new JobBuilder("tmdbCollectJob", jobRepository)
        .listener(listener)
        .start(tmdbMovieStep)
        .next(tmdbTvStep)
        .build();
  }

  @Bean
  public TmdbCollectJobListener tmdbCollectJobListener() {
    return new TmdbCollectJobListener(tmdbGenreCache, meterRegistry);
  }

  @Bean
  public Step tmdbMovieStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      @Qualifier("tmdbMovieReader") TmdbPageItemReader tmdbMovieReader,
      @Value("${batch.tmdb.collect.skip-limit}") int skipLimit) {
    return new StepBuilder("tmdbMovieStep", jobRepository)
        .<TmdbContentItem, Content>chunk(CHUNK_SIZE, transactionManager)
        .reader(tmdbMovieReader)
        .processor(processor(ContentType.MOVIE))
        .writer(writer(ContentType.MOVIE))
        // DB제약 위반 예외는 건너뜀(지금은 최대 10번 까지 건너띌 수 있도록 설정)
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
      @Value("${batch.tmdb.collect.skip-limit}") int skipLimit) {
    return new StepBuilder("tmdbTvStep", jobRepository)
        .<TmdbContentItem, Content>chunk(CHUNK_SIZE, transactionManager)
        .reader(tmdbTvReader)
        .processor(processor(ContentType.TV_SERIES))
        .writer(writer(ContentType.TV_SERIES))
        // DB제약 위반 예외는 건너뜀(지금은 최대 10번 까지 건너띌 수 있도록 설정)
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

  private ItemProcessor<TmdbContentItem, Content> processor(ContentType contentType) {
    return new TmdbContentItemProcessor(contentType, tmdbGenreCache, tmdbProperties);
  }

  private ItemWriter<Content> writer(ContentType contentType) {
    return new TmdbContentItemWriter(contentRepository, contentType, meterRegistry);
  }
}
