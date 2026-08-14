package com.codeit.mople.realtime.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.global.event.ForceLogoutReason;
import com.codeit.mople.global.event.UserAccountStatusChangedEvent;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketForceLogoutEventListener")
class WebSocketForceLogoutEventListenerTest {

  @InjectMocks
  private WebSocketForceLogoutEventListener listener;

  @Mock
  private WebSocketSessionRegistryService registryService;

  private final UUID userId = UUID.randomUUID();

  @Test
  @DisplayName("sessionInvalidated가 false면(ACCOUNT_UNLOCKED) 아무 것도 하지 않는다")
  void sessionNotInvalidated_doesNothing() {
    UserAccountStatusChangedEvent event =
        new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ACCOUNT_UNLOCKED, false);

    listener.handleUserAccountStatusChanged(event);

    verifyNoInteractions(registryService);
  }

  @Test
  @DisplayName("살아있는 WebSocket 세션이 없으면 pub/sub을 발행하지 않는다")
  void sessionInvalidated_noLiveSessions_doesNotPublish() {
    UserAccountStatusChangedEvent event =
        new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ACCOUNT_LOCKED, true);
    given(registryService.getLiveSessionIds(userId)).willReturn(Collections.emptySet());

    listener.handleUserAccountStatusChanged(event);

    verify(registryService, never()).publishForceDisconnect(any(), any());
  }

  @Test
  @DisplayName("권한 변경으로 인한 세션 무효화 시 해당 사유로 pub/sub을 발행한다")
  void roleChange_withLiveSessions_publishesWithReason() {
    UserAccountStatusChangedEvent event =
        new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ROLE_CHANGE, true);
    given(registryService.getLiveSessionIds(userId)).willReturn(Set.of("session-1"));

    listener.handleUserAccountStatusChanged(event);

    verify(registryService).publishForceDisconnect(
        userId, "권한이 변경되어 연결이 종료되었습니다. 다시 로그인해주세요.");
  }

  @Test
  @DisplayName("계정 잠금으로 인한 세션 무효화 시 해당 사유로 pub/sub을 발행한다")
  void accountLocked_withLiveSessions_publishesWithReason() {
    UserAccountStatusChangedEvent event =
        new UserAccountStatusChangedEvent(userId, ForceLogoutReason.ACCOUNT_LOCKED, true);
    given(registryService.getLiveSessionIds(userId)).willReturn(Set.of("session-1"));

    listener.handleUserAccountStatusChanged(event);

    verify(registryService).publishForceDisconnect(
        userId, "계정이 잠금 처리되어 연결이 종료되었습니다.");
  }
}
