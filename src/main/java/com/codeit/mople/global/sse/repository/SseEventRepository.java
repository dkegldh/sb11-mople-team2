package com.codeit.mople.global.sse.repository;

import com.codeit.mople.global.sse.model.SseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SseEventRepository {

  private static final String STREAM_KEY = "sse:undelivered:";

  // 사용자별 100개까지 유실 SSE 이벤트 보관(이후 큐처럼 밀림), 최대 하루까지 보관
  private static final long MAX_EVENT_COUNT = 100L;
  private static final Duration EVENT_TTL = Duration.ofHours(24);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  // 사용자별로 알림, DM 무관한 SSE 유실 이벤트를 최대 100개까지 보관
  public void save(SseEvent event) {
    String streamKey = STREAM_KEY + event.receiverId();

    try {
      String data = objectMapper.writeValueAsString(event.data());

      XAddOptions options = XAddOptions
          .maxlen(MAX_EVENT_COUNT)
          .approximateTrimming(true);

      redisTemplate.opsForStream().add(
          streamKey,
          Map.of(
              "eventId", event.id().toString(),
              "eventName", event.eventName(),
              "data", data
          ),
          options
      );

      redisTemplate.expire(streamKey, EVENT_TTL);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("SSE 이벤트 직렬화에 실패했습니다.", e);
    }

  }

  public List<SseEvent> findAfter(UUID receiverId, UUID lastEventId) {
    String streamKey = STREAM_KEY + receiverId;

    // receiver별 Stream Key에 해당하는 value들을 가져옴
    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(
        streamKey, Range.unbounded()
    );

    // 비어있을 경우 빈 List 반환
    if (records == null || records.isEmpty()) {
      return List.of();
    }

    // 결과를 반환 리스트 생성
    List<SseEvent> result = new ArrayList<>();

    // lastEventId 이후 값들을 찾기 위한 flag
    boolean found = false;

    for (MapRecord<String, Object, Object> record : records) {
      Map<Object, Object> value = record.getValue();

      UUID eventId = UUID.fromString(value.get("eventId").toString());

      // found가 true일 경우 리스트에 추가(lastEventId 이후 데이터를 추가)
      if (found) {
        result.add(toSseEvent(value, receiverId));
      }

      // lastEventId와 이벤트ID가 일치할 경우 위치를 발견함
      if (eventId.equals(lastEventId)) {
        found = true;
      }
    }

    // Redis Stream 도입 후 상한선 추가로인한 lastEvent가 밀려서 못 찾을 경우 최근 최대 100개의 이벤트만을 반환
    if (!found) {
      log.warn(
          "lastEventId를 찾지 못해 최근 최대 100개의 이벤트를 재전송: 현재 lastEventId={}, receiverId={}, Resend SSE size={}",
          lastEventId, receiverId, Math.min(records.size(), MAX_EVENT_COUNT));

      return records.stream()
          .map(record -> toSseEvent(record.getValue(), receiverId))
          .toList();
    }

    return result;
  }

  private SseEvent toSseEvent(Map<Object, Object> value, UUID receiverId) {
    try {
      UUID eventId = UUID.fromString(value.get("eventId").toString());

      return new SseEvent(
          eventId,
          receiverId,
          value.get("eventName").toString(),
          objectMapper.readValue(value.get("data").toString(), Object.class)
      );
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("SSE 이벤트 역직렬화에 실패했습니다.", e);
    }
  }

}
