package com.codeit.mople.domain.content.client.sportsdb;

import com.codeit.mople.domain.content.client.sportsdb.config.SportsDbFeignConfig;
import com.codeit.mople.domain.content.client.sportsdb.dto.SportsDbEventResponse;
import com.codeit.mople.domain.content.client.sportsdb.dto.SportsDbLeagueResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "sportsDbClient",
    url = "${external-api.sportsdb.base-url}/${external-api.sportsdb.api-key}",
    configuration = SportsDbFeignConfig.class
)
public interface SportsDbFeignClient {

  //모든 스포츠 리그 목록 조회
  @GetMapping("/all_leagues.php")
  SportsDbLeagueResponse getAllLeagues();

  //특정 일자의 경기 목록 조회
  @GetMapping("/eventsday.php")
  SportsDbEventResponse getEventsByDate(
      @RequestParam("d") String date,
      @RequestParam(value = "s", required = false) String sport
  );
}