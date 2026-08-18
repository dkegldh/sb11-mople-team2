package com.codeit.mople.global.sse.event;

import static com.codeit.mople.global.sse.SseStreamKeys.STREAM_KEY;
import static com.codeit.mople.global.sse.SseStreamKeys.FAILED_STREAM_KEY;

import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.notification.dto.response.NotificationResponse;
import com.codeit.mople.global.sse.service.SseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisBusyException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "sse.stream-consumer.enabled", havingValue = "true", matchIfMissing = true)
public class SseStreamConsumer {

  // Redis Stream Consumer 그룹
  private static final String GROUP_NAME = "sse-servers";

  // 일정 시간 이상 ACK 되지 않은 Pending 이벤트를 복구(Redis Consumer 장애 시 발생)
  private static final Duration PENDING_IDLE_TIME = Duration.ofSeconds(30);

  // SSE 이벤트 처리 재시도 정책
  private static final int MAX_RETRY_COUNT = 3;

  // Redis Consumer 장애시 재시도 정책(지수백오프 적용, 최대 30초)
  private static final long INITIAL_BACKOFF_MS = 1_000L;
  private static final long MAX_BACKOFF_MS = 30_000L;

  private final StringRedisTemplate redisTemplate;
  private final SseService sseService;

  private final ObjectMapper objectMapper;
  private final Executor executor;

  public SseStreamConsumer(
      StringRedisTemplate redisTemplate,
      SseService sseService,
      ObjectMapper objectMapper,
      // 생성자 파라미터에 @Qualifier를 명시하여 원하는 Executor를 주입
      @Qualifier("sseStreamConsumerExecutor") Executor executor
  ) {
    this.redisTemplate = redisTemplate;
    this.sseService = sseService;
    this.objectMapper = objectMapper;
    this.executor = executor;
  }

  @Value("${sse.server-id}")
  private String serverId;

  // Consume 실행 관리
  private final AtomicBoolean running = new AtomicBoolean(true);

  @PostConstruct
  public void start() {
    executor.execute(this::consume);
  }

  @PreDestroy
  public void stop() {
    running.set(false);
  }

  private void consume() {
    StreamOperations<String, String, String> streamOperations = redisTemplate.opsForStream();

    // Consumer Group에 Consumer 등록
    Consumer consumer = Consumer.from(GROUP_NAME, serverId);

    String streamKey = STREAM_KEY + serverId;

    // Consumer Group 없으면 생성
    createGroupIfNotExists(streamOperations, streamKey);

    // 1초
    long backOffMs = INITIAL_BACKOFF_MS;

    // 초기는 현재 시각, 이후 마지막으로 recoverPending(ACK 받지 못한 Pending 이벤트 복구) 호출 시각
    long lastPendingRecoveryAt = System.currentTimeMillis();

    // 서버 종료 전까지 계속 Stream을 소비함(block을 통해 처리할 이벤트 없으면 sleep 상태)
    while (running.get() && !Thread.currentThread().isInterrupted()) {
      try {
        long now = System.currentTimeMillis();

        // ACK을 받지 못해 남아있는 Pending 이벤트 복구를 약 30초마다 실행함
        if (now - lastPendingRecoveryAt >= PENDING_IDLE_TIME.toMillis()) {
          recoverPending(streamOperations, consumer, streamKey);

          // lastPending 갱신
          lastPendingRecoveryAt = now;
        }

        List<MapRecord<String, String, String>> records = streamOperations.read(
            consumer,
            StreamReadOptions.empty()
                .count(100)
                .block(Duration.ofSeconds(1)), // 처리할 이벤트가 없으면 최대 1초 동안 새 이벤트를 기다림
            StreamOffset.create(streamKey, ReadOffset.lastConsumed()
            )
        );

        // 처리할 이벤트가 저장이 안됐을 경우(없는경우) 다시 while문 앞으로 되돌아감
        if (records == null || records.isEmpty()) {
          continue;
        }

        // Redis Stream의 SSE 이벤트 처리
        for (MapRecord<String, String, String> record : records) {
          handle(record, streamOperations, streamKey);
        }

        // 정상적으로 Redis 소비가 됐으면 backOffMs 1초로 초기화
        backOffMs = INITIAL_BACKOFF_MS;

      } catch (Exception e) {
        if (Thread.currentThread().isInterrupted() || !running.get()) {
          break;
        }

        log.error("Redis Stream Consumer 처리 중 오류 발생, {}ms 후 재시도:",
            backOffMs, e);

        try {
          Thread.sleep(backOffMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }

        // 1, 2, 4, ..., 30초, ...마다 재시도
        backOffMs = Math.min(backOffMs * 2, MAX_BACKOFF_MS);
      }
    }

    log.info("SSE Stream Consumer 종료: serverId={}", serverId);
  }

  // 서버 시작 시 Consumer Group이 없으면 생성
  private void createGroupIfNotExists(
      StreamOperations<String, String, String> streamOperations,
      String streamKey
  ) {
    try {
      streamOperations.createGroup(streamKey, ReadOffset.from("0"), GROUP_NAME);
    } catch (RedisSystemException e) {
      // 이미 생성된 Consumer Group일 경우 예외 발생
      if (e.getCause() instanceof RedisBusyException) {
        log.debug("SSE Stream Consumer Group이 이미 존재: streamKey={}, group={}",
            streamKey, GROUP_NAME);
        return;
      }
      log.error("SSE Stream Consumer Group 생성 실패: streamKey={}, group={}",
          streamKey, GROUP_NAME, e);
      throw e;
    }
  }

  private void recoverPending(
      StreamOperations<String, String, String> streamOperations,
      Consumer consumer,
      String streamKey
  ) {
    PendingMessages pendingMessages = streamOperations.pending(
        streamKey, GROUP_NAME, Range.unbounded(), 100
    );

    for (PendingMessage pendingMessage : pendingMessages) {
      // 아직 처리 중일 가능성이 있는 이벤트는 가져오지 않음
      if (pendingMessage.getElapsedTimeSinceLastDelivery()
          .compareTo(PENDING_IDLE_TIME) < 0) {
        continue;
      }

      List<MapRecord<String, String, String>> records = streamOperations.claim(
          streamKey,
          GROUP_NAME,
          consumer.getName(),
          PENDING_IDLE_TIME,
          pendingMessage.getId()
      );

      if (records.isEmpty()) {
        continue;
      }

      for (MapRecord<String, String, String> record : records) {
        log.warn("Redis Stream Pending 이벤트 복구: recordId={}, consumer={}",
            record.getId(), consumer.getName());

        handle(record, streamOperations, streamKey);
      }
    }

  }

  private void handle(
      MapRecord<String, String, String> record,
      StreamOperations<String, String, String> streamOperations,
      String streamKey
  ) {
    Map<String, String> value = record.getValue();

    String eventId = value.get("eventId");
    String receiverId = value.get("receiverId");
    String eventName = value.get("eventName");
    String data = value.get("data");

    Exception lastException = null;

    for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
      try {
        Object eventData = deserialize(eventName, data);

        sseService.sendFromStream(
            UUID.fromString(eventId),
            UUID.fromString(receiverId),
            eventName,
            eventData
        );

        // Redis Stream에 이벤트 처리가 완료되었음을 알림
        streamOperations.acknowledge(streamKey, GROUP_NAME, record.getId());

        // for문(재시도) 스킵
        return;
      } catch (Exception e) {
        lastException = e;

        log.warn("SSE Stream 이벤트 처리 실패: recordId={}, attempt={}/{}",
            record.getId(), attempt, MAX_RETRY_COUNT, e);

        // 실패로 간주되어 for문 끝날때까지 순환
        if (attempt < MAX_RETRY_COUNT) {
          sleepBackoff(attempt);
        }
      }
    }

    handleFinalFailure
        (record, eventId, receiverId, eventName, data, lastException, streamOperations, streamKey);
  }

  private Object deserialize(String eventName, String data) throws JsonProcessingException {

    return switch (eventName) {
      case "direct-messages" -> objectMapper.readValue(data, DirectMessageDto.class);
      case "notifications" -> objectMapper.readValue(data, NotificationResponse.class);

      default -> throw new IllegalArgumentException("지원하지 않는 SSE 이벤트: " + eventName);
    };
  }

  private void sleepBackoff(int attempt) {
    // ex) attempt가 2일 경우 1L << 1은 0001 → 0010으로 2가 됨
    long delay = 100L * (1L << (attempt - 1));

    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();

      throw new IllegalStateException("SSE Stream 재시도 대기 중 인터럽트 발생", e);
    }
  }

  // 재시도 이후 최종 실패시 "sse:events:failed"에 저장
  private void handleFinalFailure(
      MapRecord<String, String, String> record,
      String eventId,
      String receiverId,
      String eventName,
      String data,
      Exception exception,
      StreamOperations<String, String, String> streamOperations,
      String streamKey
  ) {

    try {
      Map<String, String> failed = new HashMap<>();
      failed.put("recordId", record.getId().getValue());
      failed.put("eventId", eventId);
      failed.put("receiverId", receiverId);
      failed.put("eventName", eventName);
      failed.put("data", data);
      failed.put("serverId", serverId);
      failed.put("error", exception == null
          ? "Unknown"
          : String.valueOf(exception.getMessage()));
      streamOperations.add(FAILED_STREAM_KEY, failed);

      log.error("SSE Stream 이벤트 최종 실패: recordId={}, receiverId={}",
          record.getId(), receiverId, exception);
    } catch (Exception e) {
      log.error("SSE Stream 최종 실패 처리 중 오류 발생: recordId={}", record.getId(), e);
    } finally {
      // 저장 실패 여부와 무관하게 ACK하여 무한 재시도 방지
      streamOperations.acknowledge(streamKey, GROUP_NAME, record.getId());
    }

  }

}
