package com.codeit.mople.domain.content.client.sportsdb.batch;

import com.codeit.mople.domain.content.client.sportsdb.dto.SportsDbEventDto;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SportsDbItemProcessor implements ItemProcessor<SportsDbEventDto, Content> {

  @Override
  public Content process(SportsDbEventDto dto) {
    //무효한 데이터(필수값 누락) 검증 및 필터링
    if (dto.idEvent() == null || dto.idEvent().isBlank()
        || dto.strEvent() == null || dto.strEvent().isBlank()
        || dto.dateEvent() == null
        || dto.strSport() == null || dto.strSport().isBlank()
        || dto.strLeague() == null || dto.strLeague().isBlank()) {
      log.warn("유효하지 않은 이벤트 데이터 필터링(스킵) - idEvent: {}", dto.idEvent());
      return null; //null을 반환하면 Writer로 넘어가지 않고 스킵
    }
    //외부 DTO를 도메인의 Content 엔티티로 변환
    String title = dto.strEvent();
    String description = String.format("%s vs %s 경기 입니다. 일자: %s, 시간: %s",
        dto.strHomeTeam(), dto.strAwayTeam(), dto.dateEvent(), dto.strTime());
    String thumbnailUrl = dto.strThumb();
    List<String> tags = List.of("Sports", dto.strSport(), dto.strLeague());

    //ContentType은 임시로 SPORT 사용, 생성자에 dto.idEvent()를 외부 식별자로 전달
    return new Content(ContentType.valueOf("SPORT"), title, description,
        thumbnailUrl, tags, dto.idEvent());
  }
}