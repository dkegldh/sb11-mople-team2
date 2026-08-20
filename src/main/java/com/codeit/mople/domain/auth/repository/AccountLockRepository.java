package com.codeit.mople.domain.auth.repository;

import com.codeit.mople.domain.user.repository.UserRepository;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountLockRepository {

  private static final String KEY_PREFIX = "account:locked:";
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final RedisTemplate<String, Object> redisTemplate;
  private final UserRepository userRepository;

  public void lock(UUID userId) {
    redisTemplate.opsForValue().set(KEY_PREFIX + userId, Boolean.TRUE, CACHE_TTL);
  }

  public void unlock(UUID userId) {
    redisTemplate.delete(KEY_PREFIX + userId);
  }

  public boolean isLocked(UUID userId) {
    Object cached = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
    if(cached != null) {
      return Boolean.TRUE.equals(cached);
    }
    boolean locked = userRepository.existsByIdAndLockedTrue(userId);
    redisTemplate.opsForValue().set(KEY_PREFIX + userId, locked,CACHE_TTL);
    return locked;
  }
}
