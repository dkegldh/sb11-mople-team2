package com.codeit.mople.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

// EnableSchedulerLock -> ShedLock 기능을 활성화, lock을 최대 10분까지 제한
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

  // (RedisConnectionFactory: redis서버 접속 통로 / redis 저장소 이름)
  @Bean
  public LockProvider lockProvider(
      RedisConnectionFactory connectionFactory,
      @Value("${redis.namespace}") String namespace) {

    // Redis에 락을 쓰고 지우는 역할
    return new RedisLockProvider(connectionFactory, namespace);
  }
}