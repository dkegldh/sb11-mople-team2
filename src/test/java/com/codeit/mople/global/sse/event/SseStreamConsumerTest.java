package com.codeit.mople.global.sse.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.global.sse.service.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class SseStreamConsumerTest {

  @Mock
  private SseService sseService;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private StreamOperations<String, String, String> streamOperations;

  @Mock
  private MapRecord<String, String, String> record;

  @InjectMocks
  private SseStreamConsumer streamConsumer;

  private static final String STREAM_KEY = "sse:events:";
  private static final String GROUP_NAME = "sse-servers";
  private static final String FAILED_STREAM_KEY = "sse:events:failed:";
  private static final String SERVER_ID = "server-1";

  private UUID eventId;
  private UUID receiverId;

  private RecordId recordId;

  private DirectMessageDto eventData;
  private PendingMessage pendingMessage;
  private PendingMessages pendingMessages;

  @BeforeEach
  void setUp() {
    eventId = UUID.randomUUID();
    receiverId = UUID.randomUUID();

    eventData = mock(DirectMessageDto.class);

    ReflectionTestUtils.setField(streamConsumer, "serverId", SERVER_ID);
  }

  @Nested
  @DisplayName("이벤트 처리")
  class Handle {

    @BeforeEach
    void setUp() {
      recordId = RecordId.of("1-0");
      record = createRecord();
    }

    @Test
    @DisplayName("이벤트 처리 성공 - SSE 전송 및 ACK 완료")
    void handle_success() throws Exception {
      // given

      // BeforeEach에서 eventId, receiverId, record, eventData를 초기화

      when(objectMapper.readValue("{\"id\":\"test\"}", DirectMessageDto.class))
          .thenReturn(eventData);

      // when
      invokeHandle(record);

      // then
      verify(sseService).sendFromStream(eventId, receiverId, "direct-messages", eventData);
      verify(streamOperations).acknowledge(STREAM_KEY + SERVER_ID, GROUP_NAME, record.getId());
    }

    @Test
    @DisplayName("이벤트 처리 성공 - 전송 실패 후 재시도하여 전송 완료")
    void handle_retrySuccess() throws Exception {
      // given

      // BeforeEach에서 eventId, receiverId, record, eventData를 초기화

      when(objectMapper.readValue("{\"id\":\"test\"}", DirectMessageDto.class))
          .thenReturn(eventData);

      doThrow(new RuntimeException("SSE 전송 실패")).doNothing()  // 1회 실패 처리(2개있으면 2회, 생략 시 성공 처리X)
          .when(sseService)
          .sendFromStream(
              eventId,
              receiverId,
              "direct-messages",
              eventData
          );

      // when
      invokeHandle(record);

      // then
      // 1번 실패 후 2번째는 성공해야 함
      verify(sseService, times(2))
          .sendFromStream(eventId, receiverId, "direct-messages", eventData);

      verify(streamOperations).acknowledge(STREAM_KEY + SERVER_ID, GROUP_NAME, record.getId());
      verify(streamOperations, never()).add(eq(FAILED_STREAM_KEY), any());
    }

    @Test
    @DisplayName("이벤트 처리 실패 - 재시도 횟수 초과 후 최종 실패 처리")
    void handle_fail_retryFail() throws Exception {
      // given

      // BeforeEach에서 eventId, receiverId, record, eventData를 초기화

      when(objectMapper.readValue("{\"id\":\"test\"}", DirectMessageDto.class))
          .thenReturn(eventData);

      doThrow(new RuntimeException("SSE 전송 실패"))
          .when(sseService)
          .sendFromStream(
              eventId,
              receiverId,
              "direct-messages",
              eventData
          );

      // when
      invokeHandle(record);

      // then
      // 3번 재시도 하기 때문에 3회 호출되고 그 이후로 최종 실패가 되어야 함
      verify(sseService, times(3))
          .sendFromStream(eventId, receiverId, "direct-messages", eventData);

      verify(streamOperations).add(eq(FAILED_STREAM_KEY), any());
      verify(streamOperations).acknowledge(STREAM_KEY + SERVER_ID, GROUP_NAME, record.getId());
    }

  }

  @Nested
  @DisplayName("Pending 이벤트 복구")
  class RecoverPending {

    @BeforeEach
    void setUp() {
      pendingMessage = mock(PendingMessage.class);
      pendingMessages = mock(PendingMessages.class);

      when(pendingMessages.iterator())
          .thenReturn(List.of(pendingMessage).iterator());

      when(streamOperations.pending(
          eq(STREAM_KEY + SERVER_ID),
          eq(GROUP_NAME),
          any(Range.class),
          eq(100L))
      )
          .thenReturn(pendingMessages);
    }

    @Test
    @DisplayName("Pending 이벤트 복구 성공 - 마지막 전달 후 30초 이상 경과한 이벤트 복구")
    void recoverPending_success() throws Exception {
      // given

      // BeforeEach에서 eventId, receiverId, eventData, pendingMessage, pendingMessages를 초기화

      recordId = RecordId.of("1-0");
      record = createRecord();

      when(pendingMessage.getId())
          .thenReturn(recordId);

      when(pendingMessage.getElapsedTimeSinceLastDelivery())
          .thenReturn(Duration.ofSeconds(30));

      when(streamOperations.claim(
          eq(STREAM_KEY + SERVER_ID),
          eq(GROUP_NAME),
          any(),
          eq(Duration.ofSeconds(30)),
          eq(recordId)
      ))
          .thenReturn(List.of(record));

      when(objectMapper.readValue("{\"id\":\"test\"}", DirectMessageDto.class))
          .thenReturn(eventData);

      Consumer consumer = Consumer.from(GROUP_NAME, SERVER_ID);

      // when
      invokeRecoverPending(streamOperations, consumer, STREAM_KEY + SERVER_ID);

      // then
      verify(streamOperations)
          .claim(
              STREAM_KEY + SERVER_ID,
              GROUP_NAME,
              SERVER_ID,
              Duration.ofSeconds(30),
              record.getId()
          );

      verify(sseService).sendFromStream(eventId, receiverId, "direct-messages", eventData);
      verify(streamOperations).acknowledge(STREAM_KEY + SERVER_ID, GROUP_NAME, record.getId());
    }

    @Test
    @DisplayName("Pending 이벤트 복구 성공 - 마지막 전달 후 30초 미만인 이벤트는 복구하지 않음")
    void recoverPending_success_notReached() {
      // given

      // BeforeEach에서 eventId, receiverId, eventData, pendingMessage, pendingMessages를 초기화

      when(pendingMessage.getElapsedTimeSinceLastDelivery())
          .thenReturn(Duration.ofSeconds(29));

      Consumer consumer = Consumer.from(GROUP_NAME, SERVER_ID);

      // when
      invokeRecoverPending(streamOperations, consumer, STREAM_KEY + SERVER_ID);

      // then
      verify(streamOperations, never())
          .claim(
              eq(STREAM_KEY + SERVER_ID),
              eq(GROUP_NAME),
              eq(SERVER_ID),
              eq(Duration.ofSeconds(30)),
              any()
          );

      verify(sseService, never()).sendFromStream(any(), any(), any(), any());
    }

  }

  private MapRecord<String, String, String> createRecord() {

    when(record.getId()).thenReturn(recordId);

    when(record.getValue()).thenReturn(
        Map.of(
            "eventId", eventId.toString(),
            "receiverId", receiverId.toString(),
            "eventName", "direct-messages",
            "data", "{\"id\":\"test\"}"
        )
    );

    return record;
  }

  private void invokeHandle(
      MapRecord<String, String, String> record
  ) {
    ReflectionTestUtils.invokeMethod(
        streamConsumer,
        "handle",
        record,
        streamOperations,
        STREAM_KEY + SERVER_ID
    );
  }

  private void invokeRecoverPending(
      StreamOperations<String, String, String> streamOperations,
      Consumer consumer,
      String streamKey
  ) {
    ReflectionTestUtils.invokeMethod(
        streamConsumer,
        "recoverPending",
        streamOperations,
        consumer,
        streamKey
    );
  }

}
