package com.codeit.mople.realtime.session;

import com.codeit.mople.global.event.UserAccountStatusChangedEvent;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketForceLogoutEventListener {

  private final WebSocketSessionRegistryService registryService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleUserAccountStatusChanged(UserAccountStatusChangedEvent event) {
    // ACCOUNT_UNLOCKED는 sessionInvalidated=false로 발행되므로 여기서는 걸러진다.
    if (!event.sessionInvalidated()) {
      return;
    }

    log.debug("WebSocket 강제 종료 대상 조회 시작 - userId: {}, reason: {}", event.userId(), event.reason());

    // 하트비트 지연/Redis 재시작/시계 차이로 인해 이 조회가 비어 있어도 실제 연결이
    // 살아있을 수 있다. 그래서 결과가 비어도 발행은 계속 진행하고, 실제 종료 대상은
    // 각 인스턴스의 LocalWebSocketSessionRegistry가 최종 판단한다.
    Set<String> liveSessionIds = registryService.getLiveSessionIds(event.userId());
    if (liveSessionIds.isEmpty()) {
      log.debug("Redis에 살아있는 세션 기록 없음(로컬엔 남아있을 수 있어 발행은 계속 진행) - userId: {}",
          event.userId());
    }

    String reason = switch (event.reason()) {
      case ROLE_CHANGE -> "권한이 변경되어 연결이 종료되었습니다. 다시 로그인해주세요.";
      case ACCOUNT_LOCKED -> "계정이 잠금 처리되어 연결이 종료되었습니다.";
      case ACCOUNT_UNLOCKED -> "계정 상태가 변경되어 연결이 종료되었습니다.";
    };

    registryService.publishForceDisconnect(event.userId(), reason);
    log.info("WebSocket 강제 종료 신호 발행 완료 - userId: {}, redisLiveSessionCount: {}",
        event.userId(), liveSessionIds.size());
  }
}
