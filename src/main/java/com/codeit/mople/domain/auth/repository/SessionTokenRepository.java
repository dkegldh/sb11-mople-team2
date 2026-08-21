package com.codeit.mople.domain.auth.repository;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionTokenRepository {

  private static final String KEY_PREFIX = "session:current:";

  private final StringRedisTemplate redisTemplate;

  public void save(UUID userId, String jti, Duration ttl) {
    redisTemplate.opsForValue().set(KEY_PREFIX + userId, jti, ttl);
  }

  public boolean isValid(UUID userId, String jti) {
    Object stored = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
    return jti.equals(stored);
  }

  public void invalidate(UUID userId) {
    redisTemplate.delete(KEY_PREFIX + userId);
  }
}
