package com.codeit.mople.domain.auth.security;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import com.codeit.mople.realtime.session.WebSocketSessionRegistryService;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

  private final JwtProvider jwtProvider;
  private final UserRepository userRepository;
  private final ConversationRepository conversationRepository;
  private final SessionTokenRepository sessionTokenRepository;
  private final WebSocketSessionRegistryService sessionRegistryService;

  private static final String ERROR_KEY = "reason";
  private static final String AUTH_ERROR_MESSAGE = "유효하지 않은 토큰입니다.";

  @Override
  public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message,
        StompHeaderAccessor.class);

    if (accessor == null) {
      StompCommand command = StompHeaderAccessor.wrap(message).getCommand();
      if (StompCommand.CONNECT.equals(command) || StompCommand.SUBSCRIBE.equals(command)) {
        log.error("WebSocket 처리 거부: 가변 STOMP 헤더 접근 불가 - command: {}", command);
        throw new AuthException(AuthErrorCode.INVALID_TOKEN,
            Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
      }
      return message;
    }

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      handleConnect(accessor);
      // CONNECT 처리가 정상 완료되면, 변경된 가변 헤더를 적용한 새 메시지를 빌드해서 반환
      return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      handleSubscribe(accessor);
    }
    return message;
  }

  // [인증 - Authentication] 최초 연결(CONNECT) 시 토큰 검증 및 세션 등록
  private void handleConnect(StompHeaderAccessor accessor) {
    String token = resolveToken(accessor);

    if (token == null) {
      log.warn("WebSocket 연결 실패: Authorization 헤더 누락 또는 Bearer 접두사 누락");
      throw new AuthException(AuthErrorCode.INVALID_TOKEN,
          Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
    }

    try {
      UUID userId = jwtProvider.getUserId(token);
      String tokenJti = jwtProvider.getJti(token);

      User user = userRepository.findById(userId)
          .orElseThrow(() -> {
            log.warn("WebSocket 연결 거부: DB에 존재하지 않는 유저 - userId: {}", userId);
            return new AuthException(AuthErrorCode.INVALID_TOKEN,
                Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
          });

      // 1. 중복 로그인으로 인한 이전 기기 세션 만료 검증
      if (!sessionTokenRepository.isValid(userId, tokenJti)) {
        log.warn("WebSocket 연결 거부: 만료된 토큰 세션 버전 사용 시도 - userId: {}", userId);
        throw new AuthException(AuthErrorCode.EXPIRED_SESSION,
            Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
      }
      // 2. 관리자 등에 의해 잠금 처리된 계정인지 검증
      if (user.isLocked()) {
        log.warn("WebSocket 연결 거부: 비활성화(잠금) 상태의 계정 접근 - userId: {}", userId);
        throw new AuthException(AuthErrorCode.LOCKED_ACCOUNT,
            Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
      }

      // 위의 인가 통과 시 CustomUserDetails 생성 및 WebSocket 세션 컨텍스트 내 유저 등록
      CustomUserDetails principal = new CustomUserDetails(user.getId(), user.getRole());
      UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
          principal, null, principal.getAuthorities());

      accessor.setUser(authentication);
      sessionRegistryService.registerSession(userId, accessor.getSessionId());
      log.info("WebSocket 보안 인증 및 세션 연동 성공 - userId: {}", userId);

    } catch (ExpiredJwtException e) {
      log.warn("WebSocket 연결 실패: 만료된 JWT 토큰 사용 시도 - error: {}", e.getMessage());
      throw new AuthException(AuthErrorCode.INVALID_TOKEN,
          Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));

    } catch (MalformedJwtException | SignatureException e) {
      log.error("WebSocket 보안 경고: 변조되었거나 서명이 일치하지 않는 토큰 - error: {}", e.getMessage());
      throw new AuthException(AuthErrorCode.INVALID_TOKEN,
          Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));

    } catch (IllegalArgumentException e) {
      log.error("WebSocket 연결 실패: 잘못된 인자 전달 (토큰 외 버그 가능성) - error: {}", e.getMessage());
      throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));

    } catch (JwtException e) {
      log.warn("WebSocket 연결 실패: 기타 JWT 예외 - error: {}", e.getMessage());
      throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
    }
  }

  // [인가 - Authorization] 구독(SUBSCRIBE) 처리 로직 및 화이트리스트 적용
  private void handleSubscribe(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();

    if (destination == null) {
      throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
    }

    // 개별 에러 채널을 수신할 수 있도록 구독 경로 오픈
    if (destination.startsWith("/user/queue/")) {

      if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication) || !authentication.isAuthenticated()) {
        log.warn("WebSocket 구독 거부: 인증되지 않은 유저의 에러 채널 구독 시도 - destination: {}", destination);
        throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
      }
      log.info("WebSocket 에러 채널 구독 승인 - destination: {}", destination);
      return;
    }

    // DM 구독 경로인지 확인
    if (destination.startsWith("/sub/conversations/")) {
      validateConversationSubscription(accessor, destination);
    } else if (destination.startsWith("/sub/contents/")) { //콘텐츠 시청 세션 및 실시간 채팅 구독 경로 허용
      log.info("WebSocket 콘텐츠 채널 구독 승인 - destination: {}", destination);
    } else {
      log.warn("WebSocket 구독 거부: 화이트리스트에 등록되지 않은 경로 구독 시도 - destination: {}", destination);
      throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
    }
  }

  // 대화방 구독 권한 검증 로직
  private void validateConversationSubscription(StompHeaderAccessor accessor, String destination) {

    try {
      String[] parts = destination.split("/");
      UUID conversationId = UUID.fromString(parts[3]);

      // CONNECT 시점에 세팅해둔 principal에서 userId 추출
      UsernamePasswordAuthenticationToken authenticationToken = (UsernamePasswordAuthenticationToken) accessor.getUser();
      if (authenticationToken == null) {
        throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
      }

      CustomUserDetails principal = (CustomUserDetails) authenticationToken.getPrincipal();
      UUID userId = principal.getUserId();

      // DB 조회 및 참여자 검증 로직
      boolean isParticipant = conversationRepository.existsByIdAndParticipantId(conversationId,
          userId);

      if (!isParticipant) {
        log.warn("WebSocket 구독 거부: 존재하지 않는 방이거나 참여자가 아닌 유저의 도청 시도 - userId: {}, conversationId: {}",
            userId,
            conversationId);
        throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
      }

      log.info("WebSocket 구독 인가 성공 - userId: {}, conversationId: {}", userId, conversationId);

    } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
      log.warn("WebSocket 구독 실패: 잘못된 구독 경로 형식 - destination: {}", destination);
      throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
    } catch (Exception e) {
      log.error("WebSocket 구독 검증 중 예외 발생 - destination: " + destination, e);
      throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of(ERROR_KEY, AUTH_ERROR_MESSAGE));
    }
  }

  // STOMP 네이티브 헤더 배열에서 Authorization 토큰 추출 헬퍼 메서드
  private String resolveToken(StompHeaderAccessor accessor) {
    String bearer = accessor.getFirstNativeHeader("Authorization");
    if (bearer != null && bearer.startsWith("Bearer ")) {
      return bearer.substring(7);
    }
    return null;
  }
}
