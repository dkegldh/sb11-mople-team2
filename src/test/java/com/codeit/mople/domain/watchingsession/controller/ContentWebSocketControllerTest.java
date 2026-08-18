package com.codeit.mople.domain.watchingsession.controller;

import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.watchingsession.dto.ContentChatSendRequest;
import com.codeit.mople.domain.watchingsession.service.WatchingSessionService;
import java.security.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentWebSocketControllerTest {

  @Mock
  private WatchingSessionService watchingSessionService;

  @InjectMocks
  private ContentWebSocketController controller;

  @Test
  @DisplayName("웹소켓 채팅 메시지 수신 시 서비스의 broadcastChatMessage로 정확히 위임한다")
  void sendMessage_DelegatesToService() {
    String contentIdStr = "test-content-id";
    ContentChatSendRequest request = new ContentChatSendRequest("안녕하세요!");
    Principal mockPrincipal = org.mockito.Mockito.mock(Principal.class);

    controller.sendMessage(contentIdStr, request, mockPrincipal);

    //컨트롤러가 받은 파라미터를 하나도 빠짐없이 서비스 계층으로 잘 넘겨주는지 검증
    verify(watchingSessionService).broadcastChatMessage(contentIdStr, request, mockPrincipal);
  }
}