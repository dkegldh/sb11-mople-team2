package com.codeit.mople.domain.directmessage.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.conversation.exception.ConversationErrorCode;
import com.codeit.mople.domain.conversation.exception.ConversationException;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.directmessage.event.DirectMessageLastReadAtEvent;
import com.codeit.mople.domain.directmessage.exception.DirectMessageErrorCode;
import com.codeit.mople.domain.directmessage.exception.DirectMessageException;
import com.codeit.mople.domain.directmessage.repository.DirectMessageReadRedisRepository;
import com.codeit.mople.domain.directmessage.repository.DirectMessageRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.dto.CursorResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.codeit.mople.domain.directmessage.event.DirectMessageReceivedEvent;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class DirectMessageServiceTest {

  @InjectMocks
  private DirectMessageService directMessageService;

  @Mock
  private DirectMessageRepository directMessageRepository;

  @Mock
  private ConversationRepository conversationRepository;

  @Mock
  private ApplicationEventPublisher publisher;

  @Mock
  private DirectMessageReadRedisRepository readRedisRepository;

  @Spy
  private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private UUID userAId;
  private UUID userBId;
  private UUID strangerId;
  private UUID conversationId;
  private UUID messageId;

  private User userA;
  private User userB;
  private User stranger;
  private Conversation conversation;
  private DirectMessage message;

  @BeforeEach
  void setUp() {
    //수동으로 카운터 초기화
    directMessageService.initMetrics();

    userAId = UUID.randomUUID();
    userBId = UUID.randomUUID();
    strangerId = UUID.randomUUID();
    conversationId = UUID.randomUUID();
    messageId = UUID.randomUUID();

    userA = mock(User.class);
    userB = mock(User.class);
    stranger = mock(User.class);
    conversation = mock(Conversation.class);
    message = mock(DirectMessage.class);
  }

  @Nested
  @DisplayName("sendMessage (WebSocket DM 발송 및 영속화) 테스트")
  class SendMessage {

    @Test
    @DisplayName("성공: 정당한 참여자가 메시지를 발송하면 저장되고, 대화방 메타데이터 갱신 및 워터마크 이벤트가 발행된다.")
    void success_send_message() {
      //given
      String content = "테스트 메시지";
      Instant messageCreatedAt = Instant.now();

      given(userA.getId()).willReturn(userAId);
      given(userA.getName()).willReturn("userA");
      given(userB.getId()).willReturn(userBId);
      given(conversation.getId()).willReturn(conversationId);
      given(conversation.getUserA()).willReturn(userA);
      given(conversation.getPartnerOf(userAId)).willReturn(userB);

      DirectMessage mockSavedMessage = mock(DirectMessage.class);
      given(mockSavedMessage.getId()).willReturn(messageId);
      given(mockSavedMessage.getConversation()).willReturn(conversation);
      given(mockSavedMessage.getSender()).willReturn(userA);
      given(mockSavedMessage.getReceiver()).willReturn(userB);
      given(mockSavedMessage.getContent()).willReturn(content);
      given(mockSavedMessage.getCreatedAt()).willReturn(messageCreatedAt);

      given(conversationRepository.findByIdWithUsers(conversationId))
          .willReturn(Optional.of(conversation));
      given(directMessageRepository.save(any(DirectMessage.class)))
          .willReturn(mockSavedMessage);

      //when
      DirectMessageDto result = directMessageService.sendMessage(conversationId, userAId, content);

      //then
      assertThat(result).isNotNull();
      assertThat(result.content()).isEqualTo(content);

      // 가장 최근(마지막) 메시지 및 발신자 워터마크 갱신 메서드가 호출되었는지 검증
      verify(conversation).updateLastMessage(mockSavedMessage);

      verify(publisher).publishEvent(any(DirectMessageLastReadAtEvent.class));
      verify(publisher).publishEvent(any(DirectMessageReceivedEvent.class));

      verify(readRedisRepository, never()).saveLastReadAt(any(), any(), any());
      verify(conversation, never()).updateLastReadAt(eq(userAId), any());
    }

    @Test
    @DisplayName("실패: 존재하지 않는 대화방 ID로 메시지를 보내면 CONVERSATION_NOT_FOUND 예외가 발생한다.")
    void fail_conversation_not_found() {
      //given
      given(conversationRepository.findByIdWithUsers(conversationId))
          .willReturn(Optional.empty());

      //when & then
      assertThatThrownBy(() -> directMessageService.sendMessage(conversationId, userAId, "테스트 메시지"))
          .isInstanceOf(ConversationException.class)
          .hasMessageContaining(ConversationErrorCode.CONVERSATION_NOT_FOUND.getMessage());

      verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("실패: 대화방 참여자가 아닌 제3자가 메시지 발송을 시도하면 ACCESS_DENIED 예외가 발생한다.")
    void fail_access_denied_stranger() {
      //given
      given(userA.getId()).willReturn(userAId);
      given(userB.getId()).willReturn(userBId);
      given(conversation.getId()).willReturn(conversationId);
      given(conversation.getUserA()).willReturn(userA);
      given(conversation.getUserB()).willReturn(userB);

      given(conversationRepository.findByIdWithUsers(conversationId))
          .willReturn(Optional.of(conversation));

      //when & then
      assertThatThrownBy(() -> directMessageService.sendMessage(conversationId, strangerId, "테스트 메시지"))
          .isInstanceOf(ConversationException.class);

      verifyNoInteractions(publisher);
    }
  }

  @Nested
  @DisplayName("getDirectMessages (특정 대화방의 메시지 목록 조회) 테스트")
  class GetDirectMessages {

    @Test
    @DisplayName("성공: 대화방 참여자(UserA)가 메시지 목록을 조회한다.")
    void success_get_direct_messages() {
      //given
      given(conversation.getId()).willReturn(conversationId);
      given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
      given(conversation.getUserA()).willReturn(userA);
      given(userA.getId()).willReturn(userAId);

      // 커서 페이징 관련 리퀘스트 목 세팅 동기화
      DirectMessageCursorRequest mockRequest = mock(DirectMessageCursorRequest.class);
      given(mockRequest.limit()).willReturn(20);
      given(mockRequest.sortBy()).willReturn("createdAt");
      given(mockRequest.sortDirection()).willReturn("DESCENDING");
      given(mockRequest.parseCursorToInstant()).willReturn(null);

      long totalCount = 1L;
      given(directMessageRepository.countByConversationId(conversationId)).willReturn(totalCount);

      given(directMessageRepository.findDirectMessageByCursor(eq(conversationId), eq(mockRequest), any()))
          .willReturn(List.of(message));

      Instant messageTime = Instant.now().minusSeconds(5);

      given(message.getId()).willReturn(messageId);
      given(message.getConversation()).willReturn(conversation);
      given(message.getSender()).willReturn(userA);
      given(message.getReceiver()).willReturn(userB);
      given(message.getCreatedAt()).willReturn(messageTime);
      given(message.getContent()).willReturn("안녕하세요!");

      given(userB.getId()).willReturn(userBId);

      given(readRedisRepository.saveLastReadAt(conversationId, userAId, messageTime))
          .willReturn(true);

      //when
      CursorResponse<DirectMessageDto> result = directMessageService.getDirectMessages(conversationId, userAId, mockRequest);

      //then
      assertThat(result.data()).hasSize(1);
      assertThat(result.data().get(0).content()).isEqualTo("안녕하세요!");
      assertThat(result.hasNext()).isFalse();

      verify(readRedisRepository).saveLastReadAt(conversationId, userAId, messageTime);
      verify(conversation, never()).updateLastReadAt(eq(userAId), any());
    }

    @Test
    @DisplayName("실패: 존재하지 않는 대화방을 조회하면 예외가 발생한다.")
    void fail_get_direct_messages_not_found() {
      //given
      DirectMessageCursorRequest mockRequest = mock(DirectMessageCursorRequest.class);
      given(conversationRepository.findById(conversationId)).willReturn(Optional.empty());

      //when & then
      assertThatThrownBy(() -> directMessageService.getDirectMessages(conversationId, userAId, mockRequest))
          .isInstanceOf(ConversationException.class)
          .hasMessageContaining(ConversationErrorCode.CONVERSATION_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("실패: 대화방 참여자가 아닌 제 3자가 조회를 시도하면 예외가 발생한다.")
    void fail_get_direct_messages_access_denied() {
      //given
      DirectMessageCursorRequest mockRequest = mock(DirectMessageCursorRequest.class);
      given(conversation.getId()).willReturn(conversationId);
      given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
      given(conversation.getUserA()).willReturn(userA);
      given(conversation.getUserB()).willReturn(userB);
      given(userA.getId()).willReturn(userAId);
      given(userB.getId()).willReturn(userBId);

      //when & then
      assertThatThrownBy(() -> directMessageService.getDirectMessages(conversationId, strangerId, mockRequest))
          .isInstanceOf(ConversationException.class)
          .hasMessageContaining(ConversationErrorCode.ACCESS_DENIED.getMessage());
    }
  }

  @Nested
  @DisplayName("readMessage (단건 메시지 읽음 처리) 테스트")
  class ReadMessage {

    @Test
    @DisplayName("성공 (Cache Miss): 레디스에 값이 없으면 DB를 조회하여 복구하고, 본인의 읽음 상태가 갱신된다.")
    void success_read_message() {
      //given
      Instant messageTime = Instant.now().minusSeconds(10);
      Instant pastTime = Instant.now().minusSeconds(20); // DB에 저장되어 있던 과거 시간

      given(directMessageRepository.findById(messageId)).willReturn(Optional.of(message));
      given(message.getConversation()).willReturn(conversation);
      given(conversation.getId()).willReturn(conversationId);
      given(message.getSender()).willReturn(userA);
      given(userA.getId()).willReturn(userAId);
      given(message.getReceiver()).willReturn(userB);
      given(userB.getId()).willReturn(userBId);
      given(message.getCreatedAt()).willReturn(messageTime);

      given(readRedisRepository.getCachedLastReadAt(conversationId, userBId)).willReturn(Optional.empty());
      given(conversation.getMyLastReadAt(userBId)).willReturn(pastTime);

      given(readRedisRepository.saveLastReadAt(conversationId, userBId, messageTime)).willReturn(true);

      //when
      directMessageService.readMessage(conversationId, messageId, userBId);

      //then
      verify(readRedisRepository).setCachedLastReadAt(conversationId, userBId, pastTime);

      verify(readRedisRepository).saveLastReadAt(conversationId, userBId, messageTime);
      verify(conversation, never()).updateLastReadAt(eq(userBId), any());
    }

    @Test
    @DisplayName("성공: 발신자 본인이 자기가 보낸 메시지 읽음을 시도하면 예외 없이 조기 종료(Early Return)된다.")
    void success_read_message_by_sender_is_ignored() {
      //given
      given(directMessageRepository.findById(messageId)).willReturn(Optional.of(message));
      given(message.getConversation()).willReturn(conversation);
      given(conversation.getId()).willReturn(conversationId);
      given(message.getSender()).willReturn(userA);
      given(userA.getId()).willReturn(userAId);

      // when
      directMessageService.readMessage(conversationId, messageId, userAId);

      // then
      verify(conversation, never()).updateLastReadAt(any(), any());
    }

    @Test
    @DisplayName("실패: URL의 대화방 ID와 실제 메시지의 대화방 소속이 다르면 예외가 발생한다.")
    void fail_read_message_conversation_mismatch() {
      //given
      UUID wrongConversationId = UUID.randomUUID();

      given(directMessageRepository.findById(messageId)).willReturn(Optional.of(message));
      given(message.getConversation()).willReturn(conversation);
      given(conversation.getId()).willReturn(conversationId);

      //when & then
      assertThatThrownBy(() -> directMessageService.readMessage(wrongConversationId, messageId, userAId))
          .isInstanceOf(DirectMessageException.class)
          .hasMessageContaining(DirectMessageErrorCode.DIRECT_MESSAGE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("실패: 존재하지 않는 메시지를 읽음 처리하려 하면 예왹 발생한다.")
    void fail_read_message_not_found() {
      //given
      given(directMessageRepository.findById(messageId)).willReturn(Optional.empty());

      //when & then
      assertThatThrownBy(() -> directMessageService.readMessage(conversationId, messageId, userBId))
          .isInstanceOf(DirectMessageException.class)
          .hasMessageContaining(DirectMessageErrorCode.DIRECT_MESSAGE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("실패: 수신자가 아닌 사람이 읽음 처리를 시도하면 예외가 발생한다.")
    void fail_read_message_unauthorized() {
      //given
      given(directMessageRepository.findById(messageId)).willReturn(Optional.of(message));
      given(message.getConversation()).willReturn(conversation);
      given(conversation.getId()).willReturn(conversationId);
      given(message.getSender()).willReturn(userA);
      given(userA.getId()).willReturn(userAId);
      given(message.getReceiver()).willReturn(userB);
      given(userB.getId()).willReturn(userBId);

      // when & then: stranger가 자기가 보낸 메시지를 읽음 처리하려고 시도
      assertThatThrownBy(() -> directMessageService.readMessage(conversationId, messageId, strangerId))
          .isInstanceOf(DirectMessageException.class)
          .hasMessageContaining(DirectMessageErrorCode.UNAUTHORIZED_RECEIVER.getMessage());
    }

    @Test
    @DisplayName("성공 (Fallback): Redis 저장 실패(장애) 시, DB(엔티티)에 직접 읽음 시각을 업데이트한다.")
    void success_read_message_redis_fallback() {
      //given
      Instant messageTime = Instant.now().minusSeconds(10);
      given(directMessageRepository.findById(messageId)).willReturn(Optional.of(message));
      given(message.getConversation()).willReturn(conversation);
      given(conversation.getId()).willReturn(conversationId);
      given(message.getSender()).willReturn(userA);
      given(userA.getId()).willReturn(userAId);
      given(message.getReceiver()).willReturn(userB);
      given(userB.getId()).willReturn(userBId);
      given(message.getCreatedAt()).willReturn(messageTime);

      given(readRedisRepository.getCachedLastReadAt(conversationId, userBId)).willReturn(Optional.empty());
      given(conversation.getMyLastReadAt(userBId)).willReturn(Instant.now().minusSeconds(20));

      // 레디스 저장이 실패(false 리턴)했다고 가정
      given(readRedisRepository.saveLastReadAt(conversationId, userBId, messageTime)).willReturn(false);

      //when
      directMessageService.readMessage(conversationId, messageId, userBId);

      //then
      // 1. 레디스 저장을 시도하긴 했는지 검증
      verify(readRedisRepository).saveLastReadAt(conversationId, userBId, messageTime);

      // 2. 레디스가 실패했으므로, Fallback을 타서 엔티티의 updateLastReadAt이 호출되었는지 검증
      verify(conversation).updateLastReadAt(userBId, messageTime);
    }

    @Test
    @DisplayName("성공 (Cache Hit): Redis에 읽음 시각이 있으면 DB를 조회하지 않고 바로 처리한다.")
    void success_read_message_cache_hit() {
      // given
      Instant messageTime = Instant.now().minusSeconds(10);
      Instant cachedTime = Instant.now().minusSeconds(20); // 레디스에 있던 과거 시간

      given(directMessageRepository.findById(messageId)).willReturn(Optional.of(message));
      given(message.getConversation()).willReturn(conversation);
      given(conversation.getId()).willReturn(conversationId);
      given(message.getSender()).willReturn(userA);
      given(userA.getId()).willReturn(userAId);
      given(message.getReceiver()).willReturn(userB);
      given(userB.getId()).willReturn(userBId);
      given(message.getCreatedAt()).willReturn(messageTime);

      // 레디스에 이미 값이 존재함 (Cache Hit)
      given(readRedisRepository.getCachedLastReadAt(conversationId, userBId)).willReturn(Optional.of(cachedTime));
      given(readRedisRepository.saveLastReadAt(conversationId, userBId, messageTime)).willReturn(true);

      // when
      directMessageService.readMessage(conversationId, messageId, userBId);

      // then
      // DB 조회(getMyLastReadAt)와 캐시 복구(setCachedLastReadAt)가 호출되지 않았는지 검증
      verify(conversation, never()).getMyLastReadAt(any());
      verify(readRedisRepository, never()).setCachedLastReadAt(any(), any(), any());

      verify(readRedisRepository).saveLastReadAt(conversationId, userBId, messageTime);
    }

    @Test
    @DisplayName("성공: 이미 읽은 과거의 메시지에 대해 읽음 처리를 시도하면 레디스 갱신을 생략하고 조기 종료한다.")
    void success_read_message_already_read_early_return() {
      // given
      Instant messageTime = Instant.now().minusSeconds(30);
      Instant cachedTime = Instant.now().minusSeconds(10);

      given(directMessageRepository.findById(messageId)).willReturn(Optional.of(message));
      given(message.getConversation()).willReturn(conversation);
      given(conversation.getId()).willReturn(conversationId);
      given(message.getSender()).willReturn(userA);
      given(userA.getId()).willReturn(userAId);
      given(message.getReceiver()).willReturn(userB);
      given(userB.getId()).willReturn(userBId);

      // 메시지 시각이 캐시된 시각보다 과거로 세팅
      given(message.getCreatedAt()).willReturn(messageTime);
      given(readRedisRepository.getCachedLastReadAt(conversationId, userBId)).willReturn(Optional.of(cachedTime));

      // when
      directMessageService.readMessage(conversationId, messageId, userBId);

      // then
      verify(readRedisRepository, never()).saveLastReadAt(any(), any(), any());
      verify(conversation, never()).updateLastReadAt(any(), any());
    }
  }
}
