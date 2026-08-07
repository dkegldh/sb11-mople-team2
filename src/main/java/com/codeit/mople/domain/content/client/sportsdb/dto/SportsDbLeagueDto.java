package com.codeit.mople.domain.content.client.sportsdb.dto;

//단일 리그 상세 정보
public record SportsDbLeagueDto(
    String idLeague,
    String strLeague,
    String strSport
) {}