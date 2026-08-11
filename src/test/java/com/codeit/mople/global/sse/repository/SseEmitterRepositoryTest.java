package com.codeit.mople.global.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseEmitterRepositoryTest {

  private SseEmitterRepository repository;

  private UUID receiverId;
  private SseEmitter sseEmitter;

  @BeforeEach
  void setUp() {
    repository = new SseEmitterRepository();

    receiverId = UUID.randomUUID();
    sseEmitter = mock(SseEmitter.class);
  }

  @Nested
  @DisplayName("SseEmitter 저장")
  class Save {

    @Test
    @DisplayName("SseEmitter 저장 성공 - 단일 SseEmitter 저장")
    void save_success() {
      // given

      // BeforeEach에서 receiverId, sseEmitter를 초기화

      // when
      repository.save(receiverId, sseEmitter);

      // then
      assertThat(repository.find(receiverId)).isEqualTo(sseEmitter);
    }

    @Test
    @DisplayName("SseEmitter 저장 성공 - 기존 연결 교체")
    void save_success_replace() {
      // given

      // BeforeEach에서 receiverId, sseEmitter를 초기화

      SseEmitter newEmitter = new SseEmitter();

      repository.save(receiverId, sseEmitter);

      // when
      repository.save(receiverId, newEmitter);

      // then
      assertThat(repository.find(receiverId)).isEqualTo(newEmitter);
      
      // 이전 emitter가 종료되었는지 검증
      verify(sseEmitter).complete();
    }

  }

  @Nested
  @DisplayName("SseEmitter 조회")
  class Find {

    @Test
    @DisplayName("SseEmitter 목록 조회 성공")
    void findAll_success() {
      // given

      // BeforeEach에서 receiverId, sseEmitter를 초기화
      repository.save(receiverId, sseEmitter);

      // when
      SseEmitter result = repository.find(receiverId);

      // then
      assertThat(result).isEqualTo(sseEmitter);
    }

    @Test
    @DisplayName("SseEmitter 목록 조회 성공 - receiverId가 존재하지 않을 경우 null 반환")
    void findAll_success_empty() {
      // given

      // BeforeEach에서 receiverId를 초기화

      // when
      SseEmitter result = repository.find(receiverId);

      // then
      assertThat(result).isNull();
    }

  }

  @Nested
  @DisplayName("SseEmitter 삭제")
  class Delete {

    @Test
    @DisplayName("SseEmitter 삭제 성공")
    void delete_success() {
      // given

      // BeforeEach에서 receiverId, sseEmitter를 초기화

      repository.save(receiverId, sseEmitter);

      // when
      repository.remove(receiverId, sseEmitter);

      // then
      assertThat(repository.find(receiverId)).isNull();
    }

    @Test
    @DisplayName("SseEmitter 삭제 무시 - receiverId가 존재하지 않을 경우 동작하지 않음")
    void delete_ignore_notFoundReceiverId() {
      // given

      // BeforeEach에서 receiverId, sseEmitter를 초기화

      // receiverId가 존재하지 않는 경우를 증명하기 위해 (receiverId, sseEmitter)를 save하지 않음

      // when
      repository.remove(receiverId, sseEmitter);

      // then
      assertThat(repository.find(receiverId)).isNull();
    }

    @Test
    @DisplayName("SseEmitter 삭제 무시 - 다른 SseEmitter일 경우")
    void delete_ignore_notFoundEmitter() {
      // given

      // BeforeEach에서 receiverId, sseEmitter를 초기화

      SseEmitter anotherEmitter = new SseEmitter();

      // sseEmitter2는 저장하지 않음
      repository.save(receiverId, sseEmitter);

      // when
      repository.remove(receiverId, anotherEmitter);

      // then
      assertThat(repository.find(receiverId)).isEqualTo(sseEmitter);
    }

  }

}
