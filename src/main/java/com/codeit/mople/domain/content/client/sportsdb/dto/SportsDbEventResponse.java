package com.codeit.mople.domain.content.client.sportsdb.dto;

import java.util.List;

//경기 목록 응답
public record SportsDbEventResponse(
    List<SportsDbEventDto> events
) {}