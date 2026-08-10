package com.codeit.mople.domain.content.client.tmdb.config;

import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

// Fegin 프록시 내부에서 자동으로 호출
public class TmdbFeignConfig {

  private static final String LANGUAGE = "ko-KR";

  // 커스텀 에러처리
  @Bean
  public ErrorDecoder tmdbErrorDecoder() {
    return new TmdbErrorDecoder();
  }

  // 참고: spring cloud openFeign의 기본값은 [Retryer.NEVER_RETYR] 이고 이것은 재시도 안하는것임
  // period: 계산할 재료? 실제 대기값은 period * 1.5배씩 곱해서 만들어짐 (예 : 1초 * 1.5 = 1.5초)
  // maxPeriod: 최대 대기시간 이라고 생각하면 될듯
  // maxAttempts: 총 시도 횟수3 (원 요청 1 + 재시도 2)
  @Bean
  public Retryer tmdbRetryer() {
    return new Retryer.Default(
        1000L,
        3000L,
        3);
  }

  // 모든 요청 직전에 공통 값을 채움
  @Bean
  public RequestInterceptor tmdbRequestInterceptor(TmdbProperties properties) {
    return template -> {
      template.header("Authorization", "Bearer " + properties.apiKey());
      template.query("language", LANGUAGE);
    };
  }
}

