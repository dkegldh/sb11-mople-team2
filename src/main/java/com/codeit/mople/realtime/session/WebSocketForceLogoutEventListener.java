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

    Set<String> liveSessionIds = registryService.getLiveSessionIds(event.userId());
    if (liveSessionIds.isEmpty()) {
      log.debug("강제 종료 대상 WebSocket 세션 없음 - userId: {}", event.userId());
      return;
    }

    String reason = switch (event.reason()) {
      case ROLE_CHANGE -> "권한이 변경되어 연결이 종료되었습니다. 다시 로그인해주세요.";
      case ACCOUNT_LOCKED -> "계정이 잠금 처리되어 연결이 종료되었습니다.";
      case ACCOUNT_UNLOCKED -> "계정 상태가 변경되어 연결이 종료되었습니다.";
    };

    registryService.publishForceDisconnect(event.userId(), reason);
    log.info("WebSocket 강제 종료 신호 발행 완료 - userId: {}, sessionCount: {}",
        event.userId(), liveSessionIds.size());
  }
}
