package com.codeit.mople.global.sse.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mople.global.sse.model.SseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class SseStreamRepositoryTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ObjectMapper objectMapper;

  // String(K, key) : Redis Stream의 key
  // 첫 번째 Object(HK, Hash Key) : Stream entry의 필드 이름
  // 두 번째 Object(HV, Hash Value) : Stream entry의 필드 값
  @Mock
  private StreamOperations<String, Object, Object> streamOperations;

  @InjectMocks
  private SseStreamRepository streamRepository;

  private UUID receiverId;
  private String serverId;
  private SseEvent event;

  @BeforeEach
  void setUp() {
    receiverId = UUID.randomUUID();
    serverId = "server-1";

    event = new SseEvent(
        UUID.randomUUID(),
        receiverId,
        "notifications",
        "data"
    );
  }

  @Nested
  @DisplayName("이벤트 저장")
  class Save {

    @Test
    @DisplayName("이벤트 저장 성공")
    void save_success() throws JsonProcessingException {
      // given

      // BeforeEach에서 receiverId, serverId, event를 추가

      when(redisTemplate.opsForStream())
          .thenReturn(streamOperations);
      
      // 임시 데이터 가정
      String jsonData = "\"data\"";

      when(objectMapper.writeValueAsString(event.data()))
          .thenReturn(jsonData);

      // when
      streamRepository.save(event, serverId);

      // then
      verify(objectMapper)
          .writeValueAsString(event.data());

      verify(streamOperations).add(
          eq("sse:events:server-1"),
          eq(Map.of(
              "receiverId", receiverId.toString(),
              "eventId", event.id().toString(),
              "eventName", event.eventName(),
              "data", jsonData
          ))
      );
    }

    @Test
    @DisplayName("이벤트 저장 실패 - 이벤트 데이터 직렬화 실패")
    void save_fail_serialization() throws JsonProcessingException {
      // given

      // BeforeEach에서 serverId, event를 추가

      JsonProcessingException exception =
          new JsonProcessingException("SSE 이벤트 직렬화 실패") {
          };

      when(objectMapper.writeValueAsString(event.data()))
          .thenThrow(exception);

      // when & then
      assertThatThrownBy(() -> streamRepository.save(event, serverId))
          .isInstanceOf(IllegalStateException.class);
    }
    
  }
  
}