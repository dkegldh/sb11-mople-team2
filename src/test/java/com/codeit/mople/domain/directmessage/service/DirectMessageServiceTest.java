package com.codeit.mople.domain.directmessage.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.conversation.exception.ConversationErrorCode;
import com.codeit.mople.domain.conversation.exception.ConversationException;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.dto.response.CursorResponseDirectMessageDto;
import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.directmessage.event.DirectMessageCreatedEvent;
import com.codeit.mople.domain.directmessage.exception.DirectMessageErrorCode;
import com.codeit.mople.domain.directmessage.exception.DirectMessageException;
import com.codeit.mople.domain.directmessage.repository.DirectMessageRepository;
import com.codeit.mople.domain.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
  private ApplicationEventPublisher eventPublisher;

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
    @DisplayName("성공: 정당한 참여자가 메시지를 발송하면 저장되고, 대화방 메타데이터와 발신자 워터마크가 전진한다.")
    void success_send_message() {
      //given
      String content = "테스트 메시지";
      Instant messageCreatedAt = Instant.now();

      given(userA.getId()).willReturn(userAId);
      given(userB.getId()).willReturn(userBId);
      given(conversation.getUserA()).willReturn(userA);
      given(conversation.getPartnerOf(userAId)).willReturn(userB);

      DirectMessage mockSavedMessage = mock(DirectMessage.class);
      given(mockSavedMessage.getId()).willReturn(messageId);
      given(mockSavedMessage.getConversation()).willReturn(conversation);
      given(mockSavedMessage.getSender()).willReturn(userA);
      given(mockSavedMessage.getReceiver()).willReturn(userB);
      given(mockSavedMessage.getContent()).willReturn(content);
      given(mockSavedMessage.getCreatedAt()).willReturn(messageCreatedAt);

      given(conversationRepository.findWithLockById(conversationId))
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
      verify(conversation).updateLastReadAt(userAId, messageCreatedAt);

      verify(eventPublisher).publishEvent(new DirectMessageCreatedEvent(userBId, messageId));
    }

    @Test
    @DisplayName("실패: 존재하지 않는 대화방 ID로 메시지를 보내면 CONVERSATION_NOT_FOUND 예외가 발생한다.")
    void fail_conversation_not_found() {
      //given
      given(conversationRepository.findWithLockById(conversationId))
          .willReturn(Optional.empty());

      //when & then
      assertThatThrownBy(() -> directMessageService.sendMessage(conversationId, userAId, "테스트 메시지"))
          .isInstanceOf(ConversationException.class)
          .hasMessageContaining(ConversationErrorCode.CONVERSATION_NOT_FOUND.getMessage());

      verifyNoInteractions(eventPublisher);
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

      given(conversationRepository.findWithLockById(conversationId))
          .willReturn(Optional.of(conversation));

      //when & then
      assertThatThrownBy(() -> directMessageService.sendMessage(conversationId, strangerId, "테스트 메시지"))
          .isInstanceOf(ConversationException.class);

      verifyNoInteractions(eventPublisher);
    }
  }

  @Nested
  @DisplayName("getDirectMessages (특정 대화방의 메시지 목록 조회) 테스트")
  class GetDirectMessages {

    @Test
    @DisplayName("성공: 대화방 참여자(UserA)가 메시지 목록을 조회한다.")
    void success_get_direct_messages() {
      //given
      given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
      given(conversation.getUserA()).willReturn(userA);
      given(userA.getId()).willReturn(userAId);

      // 커서 페이징 관련 리퀘스트 목 세팅 동기화
      DirectMessageCursorRequest mockRequest = mock(DirectMessageCursorRequest.class);
      given(mockRequest.limit()).willReturn(20);
      given(mockRequest.sortBy()).willReturn("createdAt");
      given(mockRequest.sortDirection()).willReturn("DESCENDING");
      given(mockRequest.parseCursorToInstant()).willReturn(null);

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

      //when
      CursorResponseDirectMessageDto result = directMessageService.getDirectMessages(conversationId, userAId, mockRequest);

      //then
      assertThat(result.data()).hasSize(1);
      assertThat(result.data().get(0).content()).isEqualTo("안녕하세요!");
      verify(conversation).updateLastReadAt(userAId, messageTime);
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
    @DisplayName("성공: 올바른 수신자가 메시지 수신 시 대화방 내 본인의 읽음 상태가 갱신된다.")
    void success_read_message() {
      //given
      Instant messageTime = Instant.now().minusSeconds(10);
      given(directMessageRepository.findById(messageId)).willReturn(Optional.of(message));
      given(message.getConversation()).willReturn(conversation);
      given(conversation.getId()).willReturn(conversationId);
      given(message.getReceiver()).willReturn(userB);
      given(userB.getId()).willReturn(userBId);
      given(message.getCreatedAt()).willReturn(messageTime);

      given(conversation.getMyLastReadAt(userBId)).willReturn(null);

      //when
      directMessageService.readMessage(conversationId, messageId, userBId);

      //then
      verify(conversation).updateLastReadAt(userBId, messageTime);
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
      given(message.getReceiver()).willReturn(userB);
      given(userB.getId()).willReturn(userBId);

      // when & then: UserA(발신자)가 자기가 보낸 메시지를 읽음 처리하려고 시도
      assertThatThrownBy(() -> directMessageService.readMessage(conversationId, messageId, userAId))
          .isInstanceOf(DirectMessageException.class)
          .hasMessageContaining(DirectMessageErrorCode.UNAUTHORIZED_RECEIVER.getMessage());
    }
  }
}
