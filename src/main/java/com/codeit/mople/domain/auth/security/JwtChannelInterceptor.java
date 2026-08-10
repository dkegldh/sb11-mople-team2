package com.codeit.mople.domain.auth.security;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
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

  @Override
  public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor != null) {
      // [인증 - Authentication] 최초 연결(CONNECT) 시 토큰 검증 및 세션 등록
      if (StompCommand.CONNECT.equals(accessor.getCommand())) {
        String token = resolveToken(accessor);

        if (token == null) {
          log.warn("WebSocket 연결 실패: Authorization 헤더 누락 또는 Bearer 접두사 누락");
          throw new AuthException(AuthErrorCode.INVALID_TOKEN,
              Map.of("message", "웹소켓 CONNECT 요청에 인증 토큰이 누락되었습니다."));
        }

        try {
          UUID userId = jwtProvider.getUserId(token);
          long tokenSessionVersion = jwtProvider.getSessionVersion(token);

          userRepository.findById(userId).ifPresentOrElse(user -> {
            // 1. 중복 로그인으로 인한 이전 기기 세션 만료 검증
            if (user.getSessionVersion() != tokenSessionVersion) {
              log.warn("WebSocket 연결 거부: 타 기기 로그인으로 인해 만료된 토큰 세션 버전 사용 시도 - userId: {}", userId);
              throw new AuthException(AuthErrorCode.EXPIRED_SESSION,
                  Map.of("reason", "세션이 만료되었습니다."));
            }
            // 2. 관리자 등에 의해 잠금 처리된 계정인지 검증
            if (user.isLocked()) {
              log.warn("WebSocket 연결 거부: 비활성화(잠금) 상태의 계정 접근 - userId: {}", userId);
              throw new AuthException(AuthErrorCode.LOCKED_ACCOUNT,
                  Map.of("reason", "접근이 제한된 계정입니다."));
            }

            // 위의 인가 통과 시 CustomUserDetails 생성 및 WebSocket 세션 컨텍스트 내 유저 등록
            CustomUserDetails principal = new CustomUserDetails(user.getId(), user.getRole());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

            accessor.setUser(authentication);
            log.info("WebSocket 보안 인증 및 세션 연동 성공 - userId: {}", userId);

          }, () -> {
            log.warn("WebSocket 연결 거부: 토큰 파싱은 되었으나 DB에 존재하지 않는 유저 ID 접근 - userId: {}", userId);
            throw new AuthException(AuthErrorCode.INVALID_TOKEN,
                Map.of("reason", "유효하지 않은 신원 정보입니다."));
          });

        } catch (ExpiredJwtException e) {
          log.warn("WebSocket 연결 실패: 만료된 JWT 토큰 사용 시도 - error: {}", e.getMessage());
          throw new AuthException(AuthErrorCode.INVALID_TOKEN,
              Map.of("authError", "EXPIRED_TOKEN"));

        } catch (MalformedJwtException | SignatureException e) {
          log.error("WebSocket 보안 경고: 변조되었거나 서명이 일치하지 않는 토큰 - error: {}", e.getMessage());
          throw new AuthException(AuthErrorCode.INVALID_TOKEN,
              Map.of("authError", "MALFORMED_TOKEN"));

        } catch (JwtException | IllegalArgumentException e) {
          log.warn("WebSocket 연결 실패: 기타 토큰 처리 예외 발생 - error: {}", e.getMessage());
          throw new AuthException(AuthErrorCode.INVALID_TOKEN,
              Map.of("authError", "INVALID_TOKEN"));
        }
      }
      // [인가 - Authorization] 구독(SUBSCRIBE) 시 대화방 참여자 여부 검증
      else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
        String destination = accessor.getDestination();

        // DM 구독 경로인지 확인
        if (destination != null && destination.startsWith("/sub/conversations/")) {
          try {
            String[] parts = destination.split("/");
            UUID conversationId = UUID.fromString(parts[3]);

            // CONNECT 시점에 세팅해둔 principal에서 userId 추출
            UsernamePasswordAuthenticationToken authenticationToken = (UsernamePasswordAuthenticationToken) accessor.getUser();
            if (authenticationToken == null) {
              throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of("reason", "인증 정보가 유실되었습니다."));
            }

            CustomUserDetails principal = (CustomUserDetails) authenticationToken.getPrincipal();
            UUID userId = principal.getUserId();

            // DB 조회 및 참여자 검증 로직
            // TODO: 추후 existsByIdAndParticipantId 전용 쿼리를 추가하여 엔티티 로딩 없이 boolean만 반환하도록 개선
            conversationRepository.findWithDetailsById(conversationId).ifPresentOrElse(conversation -> {
              if (!conversation.getUserA().getId().equals(userId) && !conversation.getUserB().getId().equals(userId)) {
                log.warn("WebSocket 구독 거부: 참여자가 아닌 유저의 도청 시도 - userId: {}, conversationId: {}", userId, conversationId);
                throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of("reason", "구독 권한이 없거나 유효하지 않은 경로입니다."));
              }
              log.info("WebSocket 구독 인가 성공 - userId: {}, conversationId: {}", userId, conversationId);
            }, () -> {
              log.warn("WebSocket 구독 거부: 존재하지 않는 방 구독 시도 - userId: {}, conversationId: {}", userId, conversationId);
              throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of("reason", "구독 권한이 없거나 유효하지 않은 경로입니다."));
            });

          } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            log.warn("WebSocket 구독 실패: 잘못된 구독 경로 형식 - destination: {}", destination);
            throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of("reason", "잘못된 구독 요청 경로입니다."));
          }
        }
      }
    }

    return message;
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
