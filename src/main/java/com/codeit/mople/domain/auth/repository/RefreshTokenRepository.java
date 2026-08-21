package com.codeit.mople.domain.auth.repository;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepository {

  private static final String KEY_PREFIX = "refresh:";

  private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>(
      "if redis.call('get', KEYS[1]) == ARGV[1] then "
          + "redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3]) "
          + "return 1 "
          + "else "
          + "return 0 "
          + "end",
      Long.class);

  private final StringRedisTemplate redisTemplate;

  public void save(UUID userId, String refreshToken, Duration ttl) {
    redisTemplate.opsForValue().set(KEY_PREFIX + userId, refreshToken, ttl);
  }

  public boolean isValid(UUID userId, String refreshToken) {
    Object stored = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
    return refreshToken.equals(stored);
  }

  // 저장된 값이 oldToken과 일치할 때만 newToken으로 원자적으로 교체
  public boolean rotate(UUID userId, String oldToken, String newToken, Duration ttl) {
    Long result = redisTemplate.execute(
        ROTATE_SCRIPT, Collections.singletonList(KEY_PREFIX + userId),
        oldToken, newToken, String.valueOf(ttl.toMillis()));
    return Long.valueOf(1L).equals(result);
  }

  public void invalidate(UUID userId) {
    redisTemplate.delete(KEY_PREFIX + userId);
  }
}
