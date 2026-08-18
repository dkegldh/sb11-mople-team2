package com.codeit.mople.global.websocket;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import java.security.Principal;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

public class WebSocketAuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      Message<?> message
  ) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null || accessor.getUser() == null) {
      throw new AuthException(
          AuthErrorCode.INVALID_TOKEN,
          Map.of("reason", "인증 정보가 없습니다.")
      );
    }

    Principal principal = accessor.getUser();

    if (!(principal instanceof Authentication authentication)) {
      throw new AuthException(
          AuthErrorCode.INVALID_TOKEN,
          Map.of("reason", "인증 정보가 없습니다.")
      );
    }

    return authentication.getPrincipal();
  }

}
