package com.codeit.mople.domain.conversation.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeit.mople.domain.conversation.dto.response.ConversationDto;
import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ConversationMapperTest {

  private ConversationMapper conversationMapper;

  private User userA;
  private User userB;
  private UUID userAId;
  private UUID userBId;

  @BeforeEach
  void setUp() {
    conversationMapper = new ConversationMapper();

    userAId = UUID.randomUUID();
    userBId = UUID.randomUUID();

    userA = mock(User.class);
    userB = mock(User.class);

    given(userA.getId()).willReturn(userAId);
    given(userA.getName()).willReturn("유저A");
    given(userA.getProfileImageUrl()).willReturn("https://imageA.com");

    given(userB.getId()).willReturn(userBId);
    given(userB.getName()).willReturn("유저B");
    given(userB.getProfileImageUrl()).willReturn("https://imageB.com");
  }

  @Nested
  @DisplayName("hasUnread 판별 로직 단위 테스트")
  class HasUnreadTest {

    @Test
    @DisplayName("1. lastMessage가 없으면 hasUnread는 false이다. (읽음)")
    void hasUnread_false_when_no_last_message() {
      // given
      Conversation conversation = mock(Conversation.class);
      given(conversation.getId()).willReturn(UUID.randomUUID());
      given(conversation.getPartnerOf(userAId)).willReturn(userB);
      given(conversation.getLastMessage()).willReturn(null);

      //when
      ConversationDto result = conversationMapper.toDto(conversation, userAId);

      //then
      assertThat(result.hasUnread()).isFalse();
      assertThat(result.lastestMessage()).isNull();
    }

    @Test
    @DisplayName("2. lastMessage의 발송자가 요청자 본인이면 hasUnread는 false이다. (읽음)")
    void hasUnread_false_when_sender_is_requester() {
      //given
      Conversation conversation = mock(Conversation.class);
      given(conversation.getId()).willReturn(UUID.randomUUID());
      given(conversation.getPartnerOf(userAId)).willReturn(userB);

      DirectMessage lastMessage = mock(DirectMessage.class);
      given(lastMessage.getSender()).willReturn(userA);
      given(lastMessage.getReceiver()).willReturn(userB);
      given(lastMessage.getConversation()).willReturn(conversation);
      given(conversation.getLastMessage()).willReturn(lastMessage);

      //when
      ConversationDto result = conversationMapper.toDto(conversation, userAId);

      //then
      assertThat(result.hasUnread()).isFalse();
    }

    @Test
    @DisplayName("3. 상대방 메시지 수신 시 myLastReadAt이 null이면 hasUnread는 true이다. (안읽음)")
    void hasUnread_true_when_partner_message_and_myLastReadAt_is_null() {
      //given
      Conversation conversation = mock(Conversation.class);
      given(conversation.getId()).willReturn(UUID.randomUUID());
      given(conversation.getPartnerOf(userAId)).willReturn(userB);

      DirectMessage lastMessage = mock(DirectMessage.class);
      given(lastMessage.getSender()).willReturn(userB);
      given(lastMessage.getReceiver()).willReturn(userA);
      given(lastMessage.getConversation()).willReturn(conversation);

      given(conversation.getLastMessage()).willReturn(lastMessage);
      given(conversation.getMyLastReadAt(userAId)).willReturn(null);

      //when
      ConversationDto result = conversationMapper.toDto(conversation, userAId);

      //then
      assertThat(result.hasUnread()).isTrue();
    }

    @Test
    @DisplayName("4. 상대방 메시지 생성 시각이 myLastReadAt과 같거나 이전이면 hasUnread는 false이다. (읽음)")
    void hasUnread_false_when_message_created_at_equals_myLastReadAt() {
      //given
      Conversation conversation = mock(Conversation.class);
      given(conversation.getId()).willReturn(UUID.randomUUID());
      given(conversation.getPartnerOf(userAId)).willReturn(userB);

      Instant now = Instant.now();

      DirectMessage lastMessage = mock(DirectMessage.class);
      given(lastMessage.getSender()).willReturn(userB);
      given(lastMessage.getReceiver()).willReturn(userA);
      given(lastMessage.getConversation()).willReturn(conversation);
      given(lastMessage.getCreatedAt()).willReturn(now);

      given(conversation.getLastMessage()).willReturn(lastMessage);
      given(conversation.getMyLastReadAt(userAId)).willReturn(now);

      //when
      ConversationDto result = conversationMapper.toDto(conversation, userAId);

      //then
      assertThat(result.hasUnread()).isFalse();
    }
  }
}
