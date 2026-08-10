package com.codeit.mople.global.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mople.global.sse.model.SseEvent;
import com.codeit.mople.global.sse.repository.SseEmitterRepository;
import com.codeit.mople.global.sse.repository.SseEventRepository;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
public class SseServiceTest {

  @Mock
  private SseEmitterRepository emitterRepository;

  @Mock
  private SseEventRepository sseEventRepository;

  @InjectMocks
  private SseService sseService;

  private UUID receiverId;
  private SseEmitter sseEmitter;

  private UUID lastEventId;
  private SseEvent sseEvent1;
  private SseEvent sseEvent2;

  @BeforeEach
  void setUp() {
    receiverId = UUID.randomUUID();
    sseEmitter = mock(SseEmitter.class);

    lastEventId = UUID.randomUUID();
    sseEvent1 = new SseEvent(
        UUID.randomUUID(),
        receiverId,
        "notifications",
        "data"
    );
    sseEvent2 = new SseEvent(
        UUID.randomUUID(),
        receiverId,
        "direct-messages",
        "data2"
    );

  }

  @Nested
  @DisplayName("SSE 연결")
  class Connect {

    @Test
    @DisplayName("SSE 연결 성공")
    void connect_success() {
      // given

      // BeforeEach에서 receiverId를 초기화

      // when
      SseEmitter result = sseService.connect(receiverId, null);

      // then
      assertThat(result).isNotNull();

      // 레포지토리 호출 검증
      verify(emitterRepository).save(receiverId, result);

      // resendEvents() 메서드 검증(내부에 sseEventRepository.findAfter 메서드 검증)
      verify(sseEventRepository, never()).findAfter(any(), any());
    }

    @Test
    @DisplayName("SSE 연결 성공 - 유실 이벤트 재전송")
    void connect_success_resendEvents() throws IOException {
      // given

      // BeforeEach에서 receiverId, lastEventId, sseEvent1, sseEvent2를 초기화

      when(sseEventRepository.findAfter(receiverId, lastEventId))
          .thenReturn(List.of(sseEvent1, sseEvent2));

      // when
      SseEmitter result = sseService.connect(receiverId, lastEventId);

      // then
      assertThat(result).isNotNull();

      verify(emitterRepository).save(receiverId, result);
      verify(sseEventRepository).findAfter(receiverId, lastEventId);
    }

  }

  @Nested
  @DisplayName("SSE 이벤트 전송")
  class Send {

    @Test
    @DisplayName("SSE 이벤트 전송 성공")
    void send_success() throws IOException {
      // given

      // BeforeEach에서 receiverId, sseEmitter를 초기화

      when(emitterRepository.find(receiverId))
          .thenReturn(sseEmitter);

      // when
      sseService.send(receiverId, "eventName", "data");

      // then
      verify(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

      verify(sseEventRepository).save(any(SseEvent.class));
    }

    @Test
    @DisplayName("SSE 이벤트 전송 무시 - 사용자의 연결이 없는 경우")
    void send_ignore_noEmitter() {
      // given
      
      // BeforeEach에서 receiverId를 초기화

      when(emitterRepository.find(receiverId))
          .thenReturn(null);

      // when
      sseService.send(receiverId, "eventName", "data");

      // then
      verify(emitterRepository).find(receiverId);
      verify(sseEventRepository).save(any(SseEvent.class));
    }

    @Test
    @DisplayName("SSE 이벤트 전송 실패 - 연결 종료")
    void send_fail_completeWithError() throws IOException {
      // given

      // BeforeEach에서 receiverId, sseEmitter를 초기화

      when(emitterRepository.find(receiverId))
          .thenReturn(sseEmitter);

      doThrow(new IOException())
          .when(sseEmitter)
          .send(any(SseEmitter.SseEventBuilder.class));

      // when
      sseService.send(receiverId, "eventName", "data");

      // then
      verify(sseEmitter).completeWithError(any(IOException.class));
    }

  }

}
