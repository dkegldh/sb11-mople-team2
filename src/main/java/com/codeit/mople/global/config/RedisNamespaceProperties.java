package com.codeit.mople.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "redis")
public record RedisNamespaceProperties(

    @NotBlank(message = "redis.namespace 가 없습니다. 프로파일 설정을 확인하세요.")
    String namespace
) {

  public static final String LOCK_SEGMENT = "lock";

  public String cacheKeyPrefix() {
    return namespace + ":cache:";
  }

  public String lockKey(String lockName) {
    return namespace + ":" + LOCK_SEGMENT + ":" + lockName;
  }
}