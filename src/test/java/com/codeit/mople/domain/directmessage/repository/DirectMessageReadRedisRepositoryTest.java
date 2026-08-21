package com.codeit.mople.domain.directmessage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class DirectMessageReadRedisRepositoryTest {

  @InjectMocks
  private DirectMessageReadRedisRepository repository;

  @Mock
  private RedisTemplate<String, Object> redisTemplate;

  @Mock
  private ValueOperations<String, Object> valueOperations;

  @Test
  @DisplayName("성공: Lua 스크립트 실행 시 Instant가 나노초 9자리의 고정 폭 문자열로 변환되어 전달된다.")
  void saveLastReadAt_FormatVerificationTest() {
    // given
    UUID convId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    // 밀리초 단위까지만 있는 시각을 생성 (예: 2026-08-20T12:00:00.123Z)
    Instant readTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
        .willReturn(1L);

    // when
    repository.saveLastReadAt(convId, userId, readTime);

    // then
    ArgumentCaptor<Object> argsCaptor = ArgumentCaptor.forClass(Object.class);
    verify(redisTemplate).execute(any(RedisScript.class), anyList(), argsCaptor.capture(), argsCaptor.capture());

    // 첫 번째 ARGV 인자가 포맷팅된 시각 문자열
    String formattedTime = (String) argsCaptor.getAllValues().get(0);

    // 9자리 나노초 정규식 패턴과 정확히 일치하는지 확인
    assertThat(formattedTime)
        .isNotNull()
        .hasSize(30) // "yyyy-MM-ddTHH:mm:ss.SSSSSSSSSZ"는 무조건 30글자
        .endsWith("Z")
        .contains("T");
    assertThat(Instant.parse(formattedTime)).isEqualTo(readTime);
  }

  @Test
  @DisplayName("성공: 캐시 복구 시 setIfAbsent를 사용하며 고정 폭 포맷으로 저장한다.")
  void setCachedLastReadAt_SuccessTest() {
    // given
    UUID convId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant readTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    String valueKey = "dm:read:" + convId + ":" + userId;

    given(redisTemplate.opsForValue()).willReturn(valueOperations);

    // when
    repository.setCachedLastReadAt(convId, userId, readTime);

    // then
    ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations).setIfAbsent(eq(valueKey), valueCaptor.capture(), eq(Duration.ofDays(7)));

    // 저장되는 값 역시 9자리 나노초 정규식을 만족하는지 검증
    assertThat(valueCaptor.getValue())
        .isNotNull()
        .hasSize(30) // "yyyy-MM-ddTHH:mm:ss.SSSSSSSSSZ"는 무조건 30글자
        .endsWith("Z")
        .contains("T");
  }

  @Test
  @DisplayName("성공: Redis에서 조회한 고정 폭 문자열을 정상적으로 Instant로 파싱한다.")
  void getCachedLastReadAt_SuccessTest() {
    // given
    UUID convId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String valueKey = "dm:read:" + convId + ":" + userId;

    // Redis에 저장되어 있다고 가정할 9자리 나노초 문자열
    String cachedString = "2026-08-20T12:00:00.123000000Z";

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get(valueKey)).willReturn(cachedString);

    // when
    Optional<Instant> result = repository.getCachedLastReadAt(convId, userId);

    // then
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(Instant.parse(cachedString));
  }
}