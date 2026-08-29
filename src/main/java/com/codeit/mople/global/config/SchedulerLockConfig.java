package com.codeit.mople.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

// EnableSchedulerLock -> ShedLock 기능을 활성화, lock을 최대 설정 시간 까지 제한
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class SchedulerLockConfig {

  // (RedisConnectionFactory: redis서버 접속 통로 / redis 저장소 이름)
  @Bean
  public LockProvider lockProvider(
      RedisConnectionFactory connectionFactory,
      RedisNamespaceProperties properties) {

    // Redis에 락을 쓰고 지우는 역할
    RedisLockProvider redisLockProvider = new RedisLockProvider.Builder(connectionFactory)
        .keyPrefix(properties.namespace())
        .environment(RedisNamespaceProperties.LOCK_SEGMENT)
        .safeUpdate(true)
        .build();

    return new LoggingLockProvider(redisLockProvider);
  }
}