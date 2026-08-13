package com.codeit.mople.global.error;

import com.codeit.mople.domain.auth.exception.AuthException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomStompErrorHandler extends StompSubProtocolErrorHandler {

  private final ObjectMapper objectMapper;

  @Override
  public @Nullable Message<byte[]> handleClientMessageProcessingError(
      @Nullable Message<byte[]> clientMessage, Throwable ex) {
    while (ex != null && !(ex instanceof AuthException)) {
      ex = ex.getCause();
    }

    if (ex instanceof AuthException authException) {
      log.warn("STOMP 인프라 레이어 예외 감지 - ERROR 프레임 생성 시작");
      return prepareErrorMessage(authException.getMessage());
    }

    return super.handleClientMessageProcessingError(clientMessage, ex);
  }

  // STOMP ERROR 규격 프레임 생성 헬퍼
  private Message<byte[]> prepareErrorMessage(String errorMessage) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
    accessor.setMessage(errorMessage);
    accessor.setNativeHeader("content-type", "application/json;charset=UTF-8");
    accessor.setLeaveMutable(true);

    // 에러 본문 데이터 바인딩
    byte[] payloadBytes;
    try {
      payloadBytes = objectMapper.writeValueAsBytes(Map.of("reason", errorMessage));
    } catch (JsonProcessingException e) {
      payloadBytes = "{\"reason\":\"오류가 발생했습니다.\"}".getBytes(StandardCharsets.UTF_8);
    }
    return MessageBuilder.createMessage(payloadBytes, accessor.getMessageHeaders());
  }
}
