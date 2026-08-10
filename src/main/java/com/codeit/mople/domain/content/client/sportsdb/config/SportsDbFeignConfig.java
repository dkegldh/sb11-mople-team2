package com.codeit.mople.domain.content.client.sportsdb.config;

import feign.Logger;
import feign.Logger.Level;
import org.springframework.context.annotation.Bean;

public class SportsDbFeignConfig {

  @Bean
  public Logger.Level feignLoggerLevel() {
    return Level.NONE; //API 키(민감정보)가 로그에 출력되지 않도록 로깅 비활성화
  }
}
