package com.codeit.mople.domain.content.client.sportsdb.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeit.mople.domain.content.client.sportsdb.dto.SportsDbEventDto;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SportsDbItemProcessorTest {

  private final SportsDbItemProcessor processor = new SportsDbItemProcessor();

  @Test
  @DisplayName("정상 DTO 데이터가 들어오면 Content 엔티티로 올바르게 변환된다")
  void process_Success_ValidDto() {
    SportsDbEventDto mockDto = mock(SportsDbEventDto.class);
    given(mockDto.idEvent()).willReturn("EVENT-001");
    given(mockDto.strEvent()).willReturn("Arsenal vs Chelsea");
    given(mockDto.dateEvent()).willReturn("2026-08-15");
    given(mockDto.strTime()).willReturn("20:00:00");
    given(mockDto.strHomeTeam()).willReturn("Arsenal");
    given(mockDto.strAwayTeam()).willReturn("Chelsea");
    given(mockDto.strSport()).willReturn("Soccer");
    given(mockDto.strLeague()).willReturn("Premier League");
    given(mockDto.strThumb()).willReturn("http://example.com/thumb.png");

    Content content = processor.process(mockDto);

    assertThat(content).isNotNull();
    assertThat(content.getExternalId()).isEqualTo("EVENT-001");
    assertThat(content.getTitle()).isEqualTo("Arsenal vs Chelsea");
    assertThat(content.getType()).isEqualTo(ContentType.SPORT);
    assertThat(content.getThumbnailUrl()).isEqualTo("http://example.com/thumb.png");
    assertThat(content.getDescription()).contains("Arsenal vs Chelsea", "2026-08-15", "20:00:00");
    assertThat(content.getTags()).containsExactly("Sports", "Soccer", "Premier League");
  }

  @Test
  @DisplayName("필수 값(idEvent)이 누락된 DTO는 필터링(null 반환)된다")
  void process_Filtered_MissingIdEvent() {
    SportsDbEventDto mockDto = mock(SportsDbEventDto.class);
    given(mockDto.idEvent()).willReturn(null); // 식별자 누락

    Content content = processor.process(mockDto);

    assertThat(content).isNull();
  }
}