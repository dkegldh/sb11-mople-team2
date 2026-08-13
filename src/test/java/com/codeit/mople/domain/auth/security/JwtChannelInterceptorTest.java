package com.codeit.mople.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class JwtChannelInterceptorTest {

  @InjectMocks
  private JwtChannelInterceptor jwtChannelInterceptor;

  @Mock
  private JwtProvider jwtProvider;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ConversationRepository conversationRepository;

  @Mock
  private MessageChannel messageChannel;

  private final String validToken = "valid.jwt.token";
  private final UUID userId = UUID.randomUUID();

  @Nested
  @DisplayName("CONNECT 요청 검증")
  class ConnectTest {

    @Test
    @DisplayName("성공: 유효한 토큰으로 CONNECT 요청 시 인증 정보가 세션에 등록된다")
    void connect_success() {
      // given
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
      accessor.addNativeHeader("Authorization", "Bearer " + validToken);
      accessor.setLeaveMutable(true); // 인터셉터에서 수정 가능하도록 스프링 헤더 불변성 우회
      Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

      User user = User.createUser("test@test.com", "password", "테스트유저");
      ReflectionTestUtils.setField(user, "id", userId);
      ReflectionTestUtils.setField(user, "sessionVersion", 1L);

      given(jwtProvider.getUserId(validToken)).willReturn(userId);
      given(jwtProvider.getSessionVersion(validToken)).willReturn(1L);
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when
      Message<?> result = jwtChannelInterceptor.preSend(message, messageChannel);

      // then
      StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
      UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) resultAccessor.getUser();

      assertThat(auth).isNotNull();
      CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();
      assertThat(principal.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("실패: Authorization 헤더가 누락되면 AuthException이 발생한다")
    void connect_fail_missing_header() {
      // given
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
      Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

      // when & then
      assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, messageChannel))
          .isInstanceOf(AuthException.class)
          .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("실패: 만료된 토큰인 경우 AuthException이 발생한다")
    void connect_fail_expired_token() {
      // given
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
      accessor.addNativeHeader("Authorization", "Bearer " + validToken);
      Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

      given(jwtProvider.getUserId(validToken)).willThrow(ExpiredJwtException.class);

      // when & then
      assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, messageChannel))
          .isInstanceOf(AuthException.class)
          .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("실패: 유저의 세션 버전이 토큰과 다르면 세션 만료 예외가 발생한다")
    void connect_fail_expired_session() {
      // given
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
      accessor.addNativeHeader("Authorization", "Bearer " + validToken);
      Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

      User user = User.createUser("test@test.com", "password", "테스트유저");
      ReflectionTestUtils.setField(user, "id", userId);
      ReflectionTestUtils.setField(user, "sessionVersion", 2L); // 세션 버전은 2, 토큰 버전은 1

      given(jwtProvider.getUserId(validToken)).willReturn(userId);
      given(jwtProvider.getSessionVersion(validToken)).willReturn(1L);
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when & then
      assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, messageChannel))
          .isInstanceOf(AuthException.class)
          .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.EXPIRED_SESSION);
    }

    @Test
    @DisplayName("실패: 관리자에 의해 잠금 처리된 계정으로 접근 시 예외가 발생한다")
    void connect_fail_locked_account() {
      // given
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
      accessor.addNativeHeader("Authorization", "Bearer " + validToken);
      accessor.setLeaveMutable(true);
      Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

      User user = User.createUser("locked@test.com", "password", "잠긴유저");
      ReflectionTestUtils.setField(user, "id", userId);
      ReflectionTestUtils.setField(user, "sessionVersion", 1L);

      user.lock();

      given(jwtProvider.getUserId(validToken)).willReturn(userId);
      given(jwtProvider.getSessionVersion(validToken)).willReturn(1L);
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when & then
      assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, messageChannel))
          .isInstanceOf(AuthException.class)
          .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOCKED_ACCOUNT);
    }
  }

  @Nested
  @DisplayName("SUBSCRIBE 요청 검증")
  class SubscribeTest {

    private final UUID conversationId = UUID.randomUUID();
    private final String validDestination = "/sub/conversations/" + conversationId;

    @Test
    @DisplayName("성공: 대화방 참여자가 올바른 경로로 SUBSCRIBE 요청 시 통과한다")
    void subscribe_success() {
      // given
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
      accessor.setDestination(validDestination);

      CustomUserDetails principal = new CustomUserDetails(userId, Role.USER);
      UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
      accessor.setUser(auth);

      Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

      given(conversationRepository.existsByIdAndParticipantId(conversationId, userId)).willReturn(true);

      // when
      Message<?> result = jwtChannelInterceptor.preSend(message, messageChannel);

      // then
      assertThat(result).isNotNull();
      verify(conversationRepository).existsByIdAndParticipantId(conversationId, userId);
    }

    @Test
    @DisplayName("실패: 화이트리스트에 없는 경로를 구독 시도하면 차단된다")
    void subscribe_fail_invalid_destination() {
      // given
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
      accessor.setDestination("/sub/leak-path"); // 화이트리스트 외 경로
      Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

      // when & then
      assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, messageChannel))
          .isInstanceOf(AuthException.class)
          .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("실패: 대화방 참여자가 아닌 유저가 구독 시도하면 차단된다")
    void subscribe_fail_not_participant() {
      // given
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
      accessor.setDestination(validDestination);

      CustomUserDetails principal = new CustomUserDetails(userId, Role.USER);
      UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
      accessor.setUser(auth);

      Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

      given(conversationRepository.existsByIdAndParticipantId(conversationId, userId)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, messageChannel))
          .isInstanceOf(AuthException.class)
          .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
    }
  }


}
