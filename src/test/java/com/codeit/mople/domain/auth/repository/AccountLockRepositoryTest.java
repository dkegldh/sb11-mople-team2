package com.codeit.mople.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.user.repository.UserRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
public class AccountLockRepositoryTest {

  @Mock
  private RedisTemplate<String, Object> redisTemplate;

  @Mock
  private ValueOperations<String, Object> valueOperations;

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private AccountLockRepository accountLockRepository;

  private UUID userId;
  private final Duration ttl = Duration.ofMinutes(5);

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @Test
  @DisplayName("TTL과 함께 true를 캐싱함")
  void lock_setsKeyWithTtl() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);

    accountLockRepository.lock(userId);

    verify(valueOperations).set("account:locked:" + userId, Boolean.TRUE, ttl);
  }

  @Test
  @DisplayName("캐시 히트(true)면 DB를 조회하지 않고 바로 반환함")
  void isLocked_returnsCachedTrue_withoutDbLookup() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("account:locked:" + userId)).willReturn(Boolean.TRUE);

    boolean result = accountLockRepository.isLocked(userId);

    assertThat(result).isTrue();
    verifyNoInteractions(userRepository);
  }

  @Test
  @DisplayName("캐시 히트(false)면 DB를 조회하지 않고 바로 반환함")
  void isLocked_returnsCachedFalse_withoutDbLookup() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("account:locked:" + userId)).willReturn(Boolean.FALSE);

    boolean result = accountLockRepository.isLocked(userId);

    assertThat(result).isFalse();
    verifyNoInteractions(userRepository);
  }

  @Test
  @DisplayName("캐시 미스면 DB로 확인하고 결과를 다시 캐싱함 (locked)")
  void isLocked_fallsBackToDbAndCaches_whenMissAndLocked() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("account:locked:" + userId)).willReturn(null);
    given(userRepository.existsByIdAndLockedTrue(userId)).willReturn(true);

    boolean result = accountLockRepository.isLocked(userId);

    assertThat(result).isTrue();
    verify(valueOperations).set("account:locked:" + userId, true, ttl);
  }

  @Test
  @DisplayName("캐시 미스면 DB로 확인하고 결과를 다시 캐싱함 (unlocked)")
  void isLocked_fallsBackToDbAndCaches_whenMissAndUnlocked() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("account:locked:" + userId)).willReturn(null);
    given(userRepository.existsByIdAndLockedTrue(userId)).willReturn(false);

    boolean result = accountLockRepository.isLocked(userId);

    assertThat(result).isFalse();
    verify(valueOperations).set("account:locked:" + userId, false, ttl);
  }
}
