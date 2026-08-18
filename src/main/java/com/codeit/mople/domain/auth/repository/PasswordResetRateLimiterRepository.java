package com.codeit.mople.domain.auth.repository;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetRateLimiterRepository {

  private static final String EMAIL_KEY_PREFIX = "reset-password:rate-limit:email:";
  private static final String IP_KEY_PREFIX = "reset-password:rate-limit:ip:";
  private static final String GLOBAL_KEY = "reset-password:rate-limit:global";

  private final RedisTemplate<String, Object> redisTemplate;

  public boolean tryAcquireByEmail(String email, Duration ttl) {
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(EMAIL_KEY_PREFIX + email, 1, ttl);
    return Boolean.TRUE.equals(acquired);
  }

  public boolean tryAcquireByIp(String ip, int maxRequests, Duration window) {
    return tryAcquireWithLimit(IP_KEY_PREFIX + ip, maxRequests, window);
  }

  public boolean tryAcquireGlobal(int maxRequests, Duration window) {
    return tryAcquireWithLimit(GLOBAL_KEY, maxRequests, window);
  }

  public boolean tryAcquireWithLimit(String key, int maxRequests, Duration window) {
    Long count = redisTemplate.opsForValue().increment(key);
    if(count != null && count == 1L) {
      redisTemplate.expire(key, window);
    }
    return count != null && count <= maxRequests;
  }
}
