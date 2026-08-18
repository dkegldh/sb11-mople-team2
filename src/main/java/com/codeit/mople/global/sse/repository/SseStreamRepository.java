package com.codeit.mople.global.sse.repository;

import static com.codeit.mople.global.sse.SseStreamKeys.STREAM_KEY;

import com.codeit.mople.global.sse.model.SseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SseStreamRepository {


  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public void save(SseEvent event, String serverId) {

    try {
      String data = objectMapper.writeValueAsString(event.data());
      String streamKey = STREAM_KEY + serverId;

      redisTemplate.opsForStream().add(
          streamKey,
          Map.of(
              "receiverId", event.receiverId().toString(),
              "eventId", event.id().toString(),
              "eventName", event.eventName(),
              "data", data
          )
      );
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("SSE 이벤트 직렬화에 실패했습니다", e);
    }
  }

}
