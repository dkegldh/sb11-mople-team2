package com.codeit.mople.global.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.codeit.mople.global.sse.model.SseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
public class SseEventRepositoryTest {

  private static final String STREAM_KEY = "sse:undelivered:";

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private StreamOperations<String, Object, Object> streamOperations;

  @InjectMocks
  private SseEventRepository sseEventRepository;

  private UUID receiverId;
  private SseEvent sseEvent1;
  private SseEvent sseEvent2;
  private SseEvent sseEvent3;

  @BeforeEach
  void setUp() {

    receiverId = UUID.randomUUID();

    sseEvent1 = new SseEvent(
        UUID.randomUUID(),
        receiverId,
        "notifications",
        "data"
    );
    sseEvent2 = new SseEvent(
        UUID.randomUUID(),
        receiverId,
        "notifications",
        "data2"
    );
    sseEvent3 = new SseEvent(
        UUID.randomUUID(),
        receiverId,
        "direct-messages",
        "data"
    );

    // Redis Stream 연산 Mock 설정
    given(redisTemplate.opsForStream())
        .willReturn(streamOperations);
  }

  @Nested
  @DisplayName("SSE 이벤트 저장")
  class Save {

    @Test
    @DisplayName("SSE 이벤트 저장 성공 - Redis Stream 저장 및 최대 이벤트 개수 유지")
    void save_success() throws Exception {
      // given

      // BeforeEach에서 receiverId, sseEvent1을 초기화

      given(objectMapper.writeValueAsString(sseEvent1.data()))
          .willReturn("\"data\"");

      // when
      sseEventRepository.save(sseEvent1);

      // then
      verify(streamOperations).add(
          eq(STREAM_KEY + receiverId),
          eq(Map.of(
              "eventId", sseEvent1.id().toString(),
              "eventName", sseEvent1.eventName(),
              "data", "\"data\""
          )),
          any()
      );
    }

  }

  @Nested
  @DisplayName("SSE 이벤트 이후 조회")
  class FindAfter {

    @Mock
    private MapRecord<String, Object, Object> record1;

    @Mock
    private MapRecord<String, Object, Object> record2;

    @Mock
    private MapRecord<String, Object, Object> record3;

    @Test
    @DisplayName("SSE 이벤트 이후 조회 성공")
    void findAfter_success() throws Exception {
      // given

      // BeforeEach에서 receiverId, sseEvent1, sseEvent2, sseEvent3을 초기화

      given(record1.getValue()).willReturn(Map.of(
          "eventId", sseEvent1.id().toString(),
          "eventName", sseEvent1.eventName(),
          "data", "\"data\""
      ));

      given(record2.getValue()).willReturn(Map.of(
          "eventId", sseEvent2.id().toString(),
          "eventName", sseEvent2.eventName(),
          "data", "\"data2\""
      ));

      given(record3.getValue()).willReturn(Map.of(
          "eventId", sseEvent3.id().toString(),
          "eventName", sseEvent3.eventName(),
          "data", "\"data\""
      ));

      // Redis Stream에 저장된 이벤트 데이터를 역직렬화
      given(objectMapper.readValue("\"data\"", Object.class))
          .willReturn("data");

      given(objectMapper.readValue("\"data2\"", Object.class))
          .willReturn("data2");

      given(streamOperations.range(
          eq(STREAM_KEY + receiverId),
          any()
      )).willReturn(List.of(
          record1,
          record2,
          record3
      ));

      // when
      List<SseEvent> result =
          sseEventRepository.findAfter(receiverId, sseEvent1.id());

      // then
      assertThat(result)
          .extracting(SseEvent::id)
          .containsExactly(
              sseEvent2.id(),
              sseEvent3.id()
          );
    }

    @Test
    @DisplayName("SSE 이벤트 이후 조회 성공 - 스트림에 이벤트가 없으면 빈 리스트 반환")
    void findAfter_success_lastEvent_emptyStream() {
      // given

      // BeforeEach에서 receiverId, sseEvent1을 초기화

      given(streamOperations.range(
          eq(STREAM_KEY + receiverId),
          any()
      )).willReturn(List.of());

      // when
      List<SseEvent> result =
          sseEventRepository.findAfter(receiverId, sseEvent1.id());

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("SSE 이벤트 이후 조회 성공 - 마지막 이벤트가 기준 이벤트이면 빈 리스트 반환")
    void findAfter_success_lastEvent() throws JsonProcessingException {
      // given

      given(streamOperations.range(
          eq(STREAM_KEY + receiverId),
          any()
      )).willReturn(List.of(
          StreamRecords.newRecord()
              .in(STREAM_KEY + receiverId)
              .ofMap(Map.of(
                  "eventId", sseEvent1.id().toString(),
                  "eventName", sseEvent1.eventName(),
                  "data", "\"data1\""
              )),
          StreamRecords.newRecord()
              .in(STREAM_KEY + receiverId)
              .ofMap(Map.of(
                  "eventId", sseEvent2.id().toString(),
                  "eventName", sseEvent2.eventName(),
                  "data", "\"data2\""
              ))
      ));

      // when
      List<SseEvent> result =
          sseEventRepository.findAfter(receiverId, sseEvent2.id());

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("SSE 이벤트 이후 조회 성공 - lastEventId가 존재하지 않는 이벤트 ID일 경우 전체 이벤트 반환")
    void findAfter_success_notFoundEvent() throws Exception {
      // given
      UUID notExistEventId = UUID.randomUUID();

      // BeforeEach에서 receiverId, sseEvent1을 초기화

      given(record1.getValue()).willReturn(Map.of(
          "eventId", sseEvent1.id().toString(),
          "eventName", sseEvent1.eventName(),
          "data", "\"data\""
      ));

      // Redis Stream에 저장된 이벤트 데이터를 역직렬화
      given(objectMapper.readValue("\"data\"", Object.class))
          .willReturn("data");

      given(streamOperations.range(
          eq(STREAM_KEY + receiverId),
          any()
      )).willReturn(List.of(record1));

      // when
      List<SseEvent> result =
          sseEventRepository.findAfter(receiverId, notExistEventId);

      // then
      // lastEventId가 이미 Redis Stream의 상한선으로 인해 삭제된 경우
      // 현재 보관 중인 이벤트 전체를 재전송 대상으로 반환
      assertThat(result)
          .extracting(SseEvent::id)
          .containsExactly(sseEvent1.id());
    }
  }
}