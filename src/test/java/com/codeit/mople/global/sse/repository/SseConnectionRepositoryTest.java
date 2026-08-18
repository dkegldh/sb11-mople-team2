package com.codeit.mople.global.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
public class SseConnectionRepositoryTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @InjectMocks
  private SseConnectionRepository connectionRepository;

  private UUID receiverId;

  @BeforeEach
  void setUp() {
    receiverId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("연결 저장")
  class Save {

    @Test
    @DisplayName("연결 저장 성공")
    void save_success() {
      // given

      // BeforeEach에서 receiverId를 초기화

      // ValueOperations<String, String> Stub 설정(설정 없으면 null 반환으로 NPE 발생)
      given(redisTemplate.opsForValue()).willReturn(valueOperations);

      String serverId = "server-1";

      // when
      connectionRepository.save(receiverId, serverId);

      // then
      verify(valueOperations).set(
          "sse:connection:" + receiverId, serverId, Duration.ofHours(2)
      );
    }

  }

  @Nested
  @DisplayName("연결 조회")
  class FindServerId {

    @Test
    @DisplayName("연결 조회 성공")
    void findServerId_success() {
      // given

      // BeforeEach에서 receiverId를 초기화

      given(redisTemplate.opsForValue()).willReturn(valueOperations);

      String serverId = "server-1";

      given(valueOperations.get("sse:connection:" + receiverId))
          .willReturn(serverId);

      // when
      String result = connectionRepository.findServerId(receiverId);

      // then
      assertThat(result).isEqualTo(serverId);
    }

    @Test
    @DisplayName("연결 조회 실패 - 연결 정보가 존재하지 않음")
    void findServerId_notFound() {
      // given

      // BeforeEach에서 receiverId를 초기화

      given(redisTemplate.opsForValue()).willReturn(valueOperations);

      given(valueOperations.get("sse:connection:" + receiverId))
          .willReturn(null);

      // when
      String result = connectionRepository.findServerId(receiverId);

      // then
      assertThat(result).isNull();
    }

  }

  @Nested
  @DisplayName("연결 삭제")
  class RemoveIfOwner {

    @Test
    @DisplayName("연결 삭제 성공")
    void removeIfOwner_success() {
      // given

      // BeforeEach에서 receiverId를 초기화

      String serverId = "server-1";

      // when
      connectionRepository.removeIfOwner(receiverId, serverId);

      // then
      verify(redisTemplate).execute(
          ArgumentMatchers.<RedisScript<Long>>any(),
          eq(List.of("sse:connection:" + receiverId)),
          eq(serverId)
      );
    }

  }

}
