package com.codeit.mople.domain.content.client.sportsdb.config;

import com.codeit.mople.domain.content.client.sportsdb.SportsDbFeignClient;
import com.codeit.mople.domain.content.client.sportsdb.dto.SportsDbEventDto;
import com.codeit.mople.domain.content.client.sportsdb.dto.SportsDbEventResponse;
import com.codeit.mople.domain.content.client.sportsdb.listener.SportsBatchJobListener;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import feign.FeignException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SportsContentBatchConfig {

  private final SportsDbFeignClient feignClient;
  private final ContentRepository contentRepository;

  //수집 작업을 총괄하는 Spring Batch Job 구성
  @Bean
  public Job sportsContentJob(
      JobRepository jobRepository,
      Step sportsContentStep,
      SportsBatchJobListener sportsBatchJobListener) {
    return new JobBuilder("sportsContentJob", jobRepository)
        .start(sportsContentStep)
        .listener(sportsBatchJobListener)
        .build();
  }

  //Chunk  기반 Spring Batch Step 구성(read -> process -> write)
  @Bean
  public Step sportsContentStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager) {

    //점점 대기 시간이 길어지는 BackOff 정책 설정
    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setInitialInterval(2000); //처음 실패 시 2초 대기
    backOffPolicy.setMultiplier(2.0); //다음 실패 시 대기 시간 2배 증가 (2초 -> 4초 -> 8초)
    backOffPolicy.setMaxInterval(10000); //최대 10초까지만 대기

    return new StepBuilder("sportsContentStep", jobRepository)
        //한번에 100개씩 데이터를 묶어서(chunk) 처리
        .<SportsDbEventDto, Content>chunk(100, transactionManager)
        .reader(sportsDbItemReader(null))
        .processor(sportsDbItemProcessor())
        .writer(sportsDbItemWriter())
        .faultTolerant()
        .retry(FeignException.class) //영구 오류(NPE 등)는 제외하고 API 통신 예외만 재시도
        .retryLimit(3) //최대 3회 재시도
        .backOffPolicy(backOffPolicy) //백오프 정책 적용
        .build();
  }

  @Bean
  @StepScope
  public ItemReader<SportsDbEventDto> sportsDbItemReader(
      @Value("#{jobParameters['runDate']}") String runDate) {
    return new ItemReader<>() {
      private Iterator<SportsDbEventDto> eventIterator;

      //Feign Client를 호출하여 경기 데이터 읽기 기능 구현
      @Override
      public SportsDbEventDto read() {
        //실행 시 최초 1회만 API를 호출하여 데이터를 메모리에 로드
        if (eventIterator == null) {
          String targetDate = (runDate != null && !runDate.isBlank()) ? runDate : LocalDate.now().toString();
          log.info("SportsDB API 조회 시작 - 일자: {}", targetDate);
          SportsDbEventResponse response = feignClient.getEventsByDate(targetDate, "Soccer");

          if (response != null && response.events() != null) {
            eventIterator = response.events().iterator();
            log.info("조회된 경기 수: {}건", response.events().size());
          } else {
            eventIterator = List.<SportsDbEventDto>of().iterator();
            log.info("조회된 경기가 없습니다.");
          }
        }
        //Iterator를 통해 Processor로 데이터를 한 건씩 전달(더 없으면 null 반환)
        return eventIterator.hasNext() ? eventIterator.next() : null;
      }
    };
  }

  @Bean
  public ItemProcessor<SportsDbEventDto, Content> sportsDbItemProcessor() {
    //무효한 데이터(필수값 누락) 검증 및 필터링
    return dto -> {
      if (dto.idEvent() == null || dto.idEvent().isBlank()
          || dto.strEvent() == null || dto.dateEvent() == null
          || dto.strSport() == null || dto.strLeague() == null) {
        log.warn("유효하지 않은 이벤트 데이터 필터링(스킵) - idEvent: {}", dto.idEvent());
        return null; //null을 반환하면 Writer로 넘어가지 않고 스킵
      }
      //외부 DTO를 도메인의 Content 엔티티로 변환
      String title = dto.strEvent();
      String description = String.format("%s vs %s 경기 입니다. 일자: %s, 시간: %s",
          dto.strHomeTeam(), dto.strAwayTeam(), dto.dateEvent(), dto.strTime());
      String thumbnailUrl = dto.strThumb();
      List<String> tags = List.of("Sports", dto.strSport(), dto.strLeague());

      //ContentType은 임시로 SPORTS 사용, 생성자에 dto.idEvent()를 외부 식별자로 전달
      return new Content(ContentType.valueOf("SPORTS"), title, description, thumbnailUrl, tags, dto.idEvent());
    };
  }

  //변환된 엔티티를 DB에 일괄 저장
  @Bean
  public ItemWriter<Content> sportsDbItemWriter() {
    return chunk -> {
      log.info("Content DB 저장 시작 - Chunk 사이즈: {}건", chunk.getItems().size());

      //c로 캐스팅하거나 map을 통해 Content 타입으로 명시
      List<Content> items = chunk.getItems().stream()
          .map(c -> (Content) c)
          .toList();

      //이번 Chunk의 외부 식별자 추출
      List<String> externalIds = items.stream()
          .map(Content::getExternalId)
          .filter(id -> id != null) // null 방어 코드 추가
          .toList();

      //DB에 이미 존재하는 식별자 조회
      List<String> existingIds = contentRepository.findByExternalIdIn(externalIds).stream()
          .map(Content::getExternalId)
          .toList();

      //기존 DB에 없는 새로운 데이터만 필터링
      List<Content> newContents = items.stream()
          .filter(content -> content.getExternalId() == null || !existingIds.contains(content.getExternalId()))
          .toList();

      //새로운 데이터만 저장
      if (!newContents.isEmpty()) {
        contentRepository.saveAll(newContents);
        log.info("새로운 경기 데이터 {}건 저장 완료 (중복 {}건 스킵)",
            newContents.size(), items.size() - newContents.size());
      } else {
        log.info("저장할 새로운 경기 데이터가 없습니다. (모두 중복)");
      }
    };
  }
}
