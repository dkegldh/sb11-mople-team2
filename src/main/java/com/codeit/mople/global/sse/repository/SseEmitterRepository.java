package com.codeit.mople.global.sse.repository;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Repository
public class SseEmitterRepository {

  private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

  public void save(UUID receiverId, SseEmitter emitter) {

    // emitter을 저장과 동시에 저장 전에 있던 값(이전 값)을 가져옴
    SseEmitter previousEmitter = emitters.put(receiverId, emitter);

    // 이전 값을 완료시킴
    if (previousEmitter != null) {
      previousEmitter.complete();
    }
  }

  // receiverId에 해당하는 SseEmitter를 반환
  public SseEmitter find(UUID receiverId) {
    return emitters.get(receiverId);
  }

  public void remove(UUID receiverId, SseEmitter emitter) {
    emitters.remove(receiverId, emitter);
  }

}
