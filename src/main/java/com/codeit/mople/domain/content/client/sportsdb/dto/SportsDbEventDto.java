package com.codeit.mople.domain.content.client.sportsdb.dto;

//단일 경기 상세
public record SportsDbEventDto(
    String idEvent,
    String strEvent,       // 경기 제목
    String strSport,       // 스포츠 종목
    String strLeague,      // 소속 리그
    String strHomeTeam,
    String strAwayTeam,
    String intHomeScore,
    String intAwayScore,
    String dateEvent,      // 경기 일자
    String strTime,        // 경기 시간
    String strThumb        // 경기 썸네일 이미지 URL
) {

}
