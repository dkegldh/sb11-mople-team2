package com.codeit.mople.global.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.global.sse.model.SseEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class SseEventRepositoryTest {

  private SseEventRepository sseEventRepository;

  private UUID receiverId;
  private SseEvent sseEvent1;
  private SseEvent sseEvent2;
  private SseEvent sseEvent3;

  @BeforeEach
  void setUp() {
    sseEventRepository = new SseEventRepository();

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
  }

  @Nested
  @DisplayName("SSE 이벤트 저장")
  class Save {

    @Test
    @DisplayName("SSE 이벤트 저장 성공 - 여러 개의 SseEvent 저장 및 순서 유지")
    void save_success_multiple() {
      // given

      // BeforeEach에서 receiverId, sseEvent1, sseEvent2, sseEvent3을 초기화

      // when
      sseEventRepository.save(sseEvent1);
      sseEventRepository.save(sseEvent2);
      sseEventRepository.save(sseEvent3);

      // then
      List<SseEvent> result = sseEventRepository.findAfter(receiverId, sseEvent1.id());
      assertThat(result).containsExactly(sseEvent2, sseEvent3);
    }

  }
  
  @Nested
  @DisplayName("SSE 이벤트 이후 조회")
  class findAfter {
    
    // save_success_multiple과 로직은 동일하지만 given, when, then 단계가 일부 차이가 있음
    @Test
    @DisplayName("SSE 이벤트 이후 조회 성공")
    void findAfter_success() {
      // given

      // BeforeEach에서 receiverId, sseEvent1, sseEvent2, sseEvent3을 초기화
      sseEventRepository.save(sseEvent1);
      sseEventRepository.save(sseEvent2);
      sseEventRepository.save(sseEvent3);

      // when
      List<SseEvent> result = sseEventRepository.findAfter(receiverId, sseEvent1.id());

      // then
      assertThat(result).containsExactly(sseEvent2, sseEvent3);
    }

    @Test
    @DisplayName("SSE 이벤트 이후 조회 성공 - 마지막 이벤트일 경우 빈 리스트 반환(유실 이벤트가 존재하지 않을 경우)")
    void findAfter_success_lastEvent() {
      // given

      // BeforeEach에서 receiverId, sseEvent1을 초기화

      sseEventRepository.save(sseEvent1);

      // when
      List<SseEvent> result = sseEventRepository.findAfter(receiverId, sseEvent1.id());

      // then
      assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("SSE 이벤트 이후 조회 성공 - lastEventId가 존재하지 않는 이벤트 ID일 경우 전체 이벤트 반환")
    void findAfter_success_notFoundEvent() {
      // given
      UUID notExistEventId = UUID.randomUUID();

      // BeforeEach에서 receiverId, sseEvent1을 초기화

      sseEventRepository.save(sseEvent1);

      // when
      List<SseEvent> result = sseEventRepository.findAfter(receiverId, notExistEventId);

      // then
      assertThat(result).containsExactly(sseEvent1);
    }
    
  }

}
