package com.codeit.mople.domain.content.client.sportsdb.config;

import com.codeit.mople.domain.content.client.sportsdb.batch.SportsDbItemProcessor;
import com.codeit.mople.domain.content.client.sportsdb.batch.SportsDbItemReader;
import com.codeit.mople.domain.content.client.sportsdb.batch.SportsDbItemWriter;
import com.codeit.mople.domain.content.client.sportsdb.dto.SportsDbEventDto;
import com.codeit.mople.domain.content.client.sportsdb.listener.SportsBatchJobListener;
import com.codeit.mople.domain.content.entity.Content;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SportsContentBatchConfig {

  private final SportsDbItemReader itemReader;
  private final SportsDbItemProcessor itemProcessor;
  private final SportsDbItemWriter itemWriter;
  private final SportsBatchJobListener jobListener;

  //수집 작업을 총괄하는 Spring Batch Job 구성
  @Bean
  public Job sportsContentJob(JobRepository jobRepository, Step sportsContentStep) {
    return new JobBuilder("sportsContentJob", jobRepository)
        .start(sportsContentStep)
        .listener(jobListener)
        .build();
  }

  //Chunk  기반 Spring Batch Step 구성(read -> process -> write)
  @Bean
  public Step sportsContentStep(JobRepository jobRepository,
      PlatformTransactionManager transactionManager) {

    //점점 대기 시간이 길어지는 BackOff 정책 설정
    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setInitialInterval(2000); //처음 실패 시 2초 대기
    backOffPolicy.setMultiplier(2.0); //다음 실패 시 대기 시간 2배 증가 (2초 -> 4초 -> 8초)
    backOffPolicy.setMaxInterval(10000); //최대 10초까지만 대기

    return new StepBuilder("sportsContentStep", jobRepository)
        //한번에 100개씩 데이터를 묶어서(chunk) 처리
        .<SportsDbEventDto, Content>chunk(100, transactionManager)
        .reader(itemReader)
        .processor(itemProcessor)
        .writer(itemWriter)
        .faultTolerant()
        .retry(FeignException.class) //영구 오류(NPE 등)는 제외하고 API 통신 예외만 재시도
        .retryLimit(3) //최대 3회 재시도
        .backOffPolicy(backOffPolicy) //백오프 정책 적용
        .build();
  }
}
