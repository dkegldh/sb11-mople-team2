package com.codeit.mople.global.error;

import com.codeit.mople.domain.conversation.exception.ConversationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import java.util.Map;

// TODO: 프론트엔드 미구현 (무시됨)
@Slf4j
@ControllerAdvice
public class WebSocketExceptionHandler {

  // 대화방 예외
  @MessageExceptionHandler(ConversationException.class)
  @SendToUser("/queue/errors")
  public Map<String, String> handleConversationException(ConversationException e) {
    log.warn("WebSocket SEND 실패 (비즈니스 로직): {}", e.getMessage());
    return Map.of("reason", e.getMessage());
  }

  // DTO 검증 실패 예외 (@Valid)
  @MessageExceptionHandler(MethodArgumentNotValidException.class)
  @SendToUser("/queue/errors")
  public Map<String, String> handleValidationException(MethodArgumentNotValidException e) {
    String errorMessage = "잘못된 요청 형식입니다.";

    // BindingResult와 에러 리스트가 비어있지 않은지 안전하게 확인
    if (e.getBindingResult() != null && e.getBindingResult().hasErrors()) {
      String parsedMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();

      if (parsedMessage != null && !parsedMessage.isBlank()) {
        errorMessage = parsedMessage;
      }
    }

    log.warn("WebSocket SEND 실패 (DTO 검증): {}", errorMessage);
    return Map.of("reason", errorMessage);
  }

}
