package com.codeit.mople.domain.content.client.sportsdb.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeit.mople.domain.content.client.sportsdb.SportsDbFeignClient;
import com.codeit.mople.domain.content.client.sportsdb.dto.SportsDbEventDto;
import com.codeit.mople.domain.content.client.sportsdb.dto.SportsDbEventResponse;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
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
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@SpringBatchTest
@DisplayName("SportsDB 수집 job 통합 테스트")
class SportsContentBatchConfigTest {

  private static final String RUN_DATE = "2026-08-15";

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private JobLauncherTestUtils jobLauncherTestUtils;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private JobRepositoryTestUtils jobRepositoryTestUtils;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private MeterRegistry meterRegistry;

  @Autowired
  @Qualifier("sportsContentJob")
  private Job sportsContentJob;

  // Reader, Processor 대신 최외곽의 FeignClient(외부 API)만 모킹합니다.
  @MockitoBean
  private SportsDbFeignClient feignClient;

  @BeforeEach
  void setUp() {
    jobLauncherTestUtils.setJob(sportsContentJob);
    jobRepositoryTestUtils.removeJobExecutions();
    contentRepository.deleteAll();

    //외부 API 응답 모킹(가짜 경기 데이터 2건)
    SportsDbEventDto dto1 = createMockDto("EVENT-1", "Team A vs Team B");
    SportsDbEventDto dto2 = createMockDto("EVENT-2", "Team C vs Team D");
    SportsDbEventResponse mockResponse = new SportsDbEventResponse(List.of(dto1, dto2));

    given(feignClient.getEventsByDate(anyString(), eq("Soccer"))).willReturn(mockResponse);
  }

  @Nested
  @DisplayName("Job 실행")
  class Launch {

    @Test
    @DisplayName("기존 데이터 백업 -> 수집 완료 후 -> 백업된 구버전 데이터만 정상 삭제된다")
    void launchJob_CompletesAndSaves() throws Exception {
      //기존 데이터 1건이 DB에 이미 존재하는 상황 가정
      Content oldContent = new Content(ContentType.SPORT, "구버전 경기", "설명", "thumb.png", new ArrayList<>(), "OLD-1");
      contentRepository.save(oldContent);

      JobParameters parameters = jobParameters(RUN_DATE);

      //배치 실행(신규 데이터 2건 수집)
      JobExecution execution = jobLauncherTestUtils.launchJob(parameters);

      assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
      assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

      //구버전 1건은 삭제되고, 신규 수집된 2건만 남아야 함
      assertThat(contentRepository.count()).isEqualTo(2);

      //스텝 실행 순서와 처리량 검증(백업 -> 수집 -> 구버전 삭제 순서)
      assertThat(execution.getStepExecutions())
          .extracting(StepExecution::getStepName)
          .containsExactly(
              "backupOldSportsDataStep", //백업 스텝 먼저
              "sportsContentStep",       //수집 스텝 나중
              "deleteOldSportsDataStep"  //삭제 스텝 마지막
          );
    }

    @Test
    @DisplayName("성공하면 batch.sports.success 카운터가 1 증가한다")
    void launchJob_IncrementsSuccessCounter() throws Exception {
      double before = meterRegistry.get("batch.sports.success").counter().count();

      jobLauncherTestUtils.launchJob(jobParameters(RUN_DATE));

      assertThat(meterRegistry.get("batch.sports.success").counter().count())
          .isEqualTo(before + 1);
    }
  }

  @Nested
  @DisplayName("하루 1회 멱등성")
  class Idempotency {

    @Test
    @DisplayName("성공적으로 완료된 파라미터(날짜)로 재실행하면 JobInstanceAlreadyCompleteException 예외가 발생한다")
    void launchJob_SameRunDate_AlreadyComplete() throws Exception {
      //1차 실행(성공)
      jobLauncherTestUtils.launchJob(jobParameters(RUN_DATE));

      //2차 실행 시도 시 이미 성공한 인스턴스에 대한 예외 발생 검증
      assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParameters(RUN_DATE)))
          .isInstanceOf(org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException.class);
    }
  }

  //JobParameter 생성 헬퍼 메서드
  private JobParameters jobParameters(String runDate) {
    return new JobParametersBuilder()
        .addString("runDate", runDate)
        .toJobParameters();
  }

  //Processor에서 필터링되지 않도록 필수값을 모두 채운 가짜 DTO 생성 헬퍼
  private SportsDbEventDto createMockDto(String id, String title) {
    SportsDbEventDto mockDto = mock(SportsDbEventDto.class);
    given(mockDto.idEvent()).willReturn(id);
    given(mockDto.strEvent()).willReturn(title);
    given(mockDto.dateEvent()).willReturn("2026-08-15");
    given(mockDto.strTime()).willReturn("20:00:00");
    given(mockDto.strHomeTeam()).willReturn("Home");
    given(mockDto.strAwayTeam()).willReturn("Away");
    given(mockDto.strSport()).willReturn("Soccer");
    given(mockDto.strLeague()).willReturn("League");
    given(mockDto.strThumb()).willReturn("thumb.png");
    return mockDto;
  }

  @Nested
  @DisplayName("실패 및 재시작 시나리오")
  class FailureAndRestart {

    @Test
    @DisplayName("수집 실패 시 기존 데이터가 보존되고, 재시작 시 정상적으로 처리된다")
    void launchJob_FailsThenRestartsSuccessfully() throws Exception {
      //초기 상태: 기존 데이터 1건이 DB에 이미 존재하는 상황 가정
      Content oldContent = new Content(ContentType.SPORT, "구버전 경기", "설명", "thumb.png", new java.util.ArrayList<>(), "OLD-1");
      contentRepository.save(oldContent);

      JobParameters parameters = jobParameters(RUN_DATE);

      //외부 API 통신 실패 모킹(RuntimeException으로 즉시 실패 유도)
      given(feignClient.getEventsByDate(anyString(), eq("Soccer")))
          .willThrow(new RuntimeException("API 통신 장애 발생"));

      //1차 실행 -> 실패 확인
      JobExecution failedExecution = jobLauncherTestUtils.launchJob(parameters);
      assertThat(failedExecution.getStatus()).isEqualTo(BatchStatus.FAILED);

      //실패 후에도 기존 데이터(1건)가 삭제되지 않고 그대로 보존되었는지 검증
      assertThat(contentRepository.count()).isEqualTo(1);
      assertThat(contentRepository.findAll().get(0).getExternalId()).isEqualTo("OLD-1");

      //외부 API 통신 정상화 모킹
      SportsDbEventDto dto1 = createMockDto("EVENT-1", "Team A vs Team B");
      SportsDbEventDto dto2 = createMockDto("EVENT-2", "Team C vs Team D");
      given(feignClient.getEventsByDate(anyString(), eq("Soccer")))
          .willReturn(new SportsDbEventResponse(List.of(dto1, dto2)));

      //동일한 파라미터(RUN_DATE)로 재시작 -> 성공 확인
      JobExecution restartedExecution = jobLauncherTestUtils.launchJob(parameters);
      assertThat(restartedExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

      //재시작 후 새 데이터 2건 적재 및 기존 백업 데이터 1건 삭제 완료 검증
      assertThat(contentRepository.count()).isEqualTo(2);
      assertThat(contentRepository.findAll())
          .extracting(Content::getExternalId)
          .containsExactlyInAnyOrder("EVENT-1", "EVENT-2");
    }
  }
}