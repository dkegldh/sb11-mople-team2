package com.codeit.mople.global.sse.service;

import com.codeit.mople.global.sse.model.SseEvent;
import com.codeit.mople.global.sse.repository.SseConnectionRepository;
import com.codeit.mople.global.sse.repository.SseEmitterRepository;
import com.codeit.mople.global.sse.repository.SseEventRepository;
import com.codeit.mople.global.sse.repository.SseStreamRepository;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {

  private static final long TIMEOUT = 60 * 60 * 1000L; // 1시간

  private final SseEmitterRepository emitterRepository;
  private final SseEventRepository eventRepository;
  private final SseConnectionRepository connectionRepository;
  private final SseStreamRepository streamRepository;

  @Value("${sse.server-id}")
  private String serverId;

  public SseEmitter connect(UUID receiverId, UUID lastEventId) {
    SseEmitter emitter = new SseEmitter(TIMEOUT);

    // 입력, 반환 둘 다 없음(Runnable)
    emitter.onCompletion(() -> {
      log.debug("SSE 연결 종료 - receiverId={}",
          receiverId);

      emitterRepository.remove(receiverId, emitter);
      connectionRepository.removeIfOwner(receiverId, serverId);
    });
    emitter.onTimeout(() -> {
      log.debug("SSE 연결 시간 초과 - receiverId={}",
          receiverId);

      emitterRepository.remove(receiverId, emitter);
      connectionRepository.removeIfOwner(receiverId, serverId);
    });

    // Consumer(void)
    emitter.onError(throwable -> {
      log.warn("SSE 연결 오류 발생 - receiverId={}",
          receiverId, throwable);

      emitterRepository.remove(receiverId, emitter);
      connectionRepository.removeIfOwner(receiverId, serverId);
    });

    emitterRepository.save(receiverId, emitter);
    connectionRepository.save(receiverId, serverId);

    resendEvents(receiverId, lastEventId, emitter);

    return emitter;
  }

  public void send(UUID receiverId, String eventName, Object data) {
    UUID eventId = UUID.randomUUID();

    SseEvent sseEvent = new SseEvent(
        eventId,
        receiverId,
        eventName,
        data
    );

    eventRepository.save(sseEvent);

    // 어느 서버에 연결되있는지 조회
    String connectedServerId = connectionRepository.findServerId(receiverId);

    // 연결이 어떤 서버에도 없을 경우 스킵(Stream 저장소에 저장도 같이 스킵)
    if (connectedServerId == null) {
      return;
    }

    // 지금 서버와 다를 경우 스킵(Stream 저장소에 저장)
    if (!serverId.equals(connectedServerId)) {
      streamRepository.save(sseEvent, connectedServerId);
      return;
    }

    SseEmitter emitter = emitterRepository.find(receiverId);

    if (emitter == null) {
      return;
    }

    try {
      emitter.send(
          SseEmitter.event()
              .id(eventId.toString())
              .name(eventName)
              .data(data)
      );
    } catch (IOException e) {
      log.warn("SSE 전송 실패 receiverId={}",
          receiverId, e);

      // connect()의 onError 콜백 메서드 실행
      emitter.completeWithError(e);
    }
  }

  public void sendFromStream(
      UUID eventId,
      UUID receiverId,
      String eventName,
      Object data
  ) {
    SseEmitter emitter = emitterRepository.find(receiverId);

    if (emitter == null) {
      return;
    }

    try {
      emitter.send(
          SseEmitter.event()
              .id(eventId.toString())
              .name(eventName)
              .data(data)
      );
    } catch (IOException e) {
      log.warn("Redis Stream SSE 전송 실패 - receiverId={}",
          receiverId, e);

      emitter.completeWithError(e);

      throw new IllegalStateException("Redis Stream SSE 전송 실패", e);
    }
  }

  private void resendEvents(UUID receiverId, UUID lastEventId, SseEmitter emitter) {
    // lastEventId가 존재하지 않을 경우 스킵
    if (lastEventId == null) {
      return;
    }

    // 유실 이벤트들을 리스트로 가져옴(순서 보장)
    List<SseEvent> events = eventRepository.findAfter(receiverId, lastEventId);

    // 리스트의 각 SseEvent를 전송시킴
    for (SseEvent event : events) {
      try {
        emitter.send(
            SseEmitter.event()
                .id(event.id().toString())
                .name(event.eventName())
                .data(event.data())
        );
      } catch (IOException e) {
        log.warn("SSE 유실 이벤트 재전송 실패 - receiverId={}",
            receiverId, e);

        // connect()의 onError 콜백 메서드 실행
        // 해당 SSE 재전송 실패 이후의 SSE들도 막도록 설정(현재 emitter가 정상적이지 않은 상태로 간주)
        emitter.completeWithError(e);
        return;
      }
    }
  }

}
