package com.codeit.mople.domain.content.client.sportsdb.dto;

import java.util.List;

//리그 목록 최상위 응답
public record SportsDbLeagueResponse(
    List<SportsDbLeagueDto> leagues
) {}