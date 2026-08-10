package com.codeit.mople.domain.conversation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeit.mople.domain.conversation.exception.ConversationErrorCode;
import com.codeit.mople.domain.conversation.exception.ConversationException;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConversationTest {

  private User userA;
  private User userB;
  private UUID userAId;
  private UUID userBId;
  private UUID strangerId;

  @BeforeEach
  void setUp() {
    userAId = UUID.randomUUID();
    userBId = UUID.randomUUID();
    strangerId = UUID.randomUUID();

    userA = mock(User.class);
    userB = mock(User.class);

    given(userA.getId()).willReturn(userAId);
    given(userB.getId()).willReturn(userBId);
  }

  @Nested
  @DisplayName("대화방 생성 테스트")
  class CreateConversation {

    @Test
    @DisplayName("성공: 빈 방 생성 시 lastMessageAt이 현재 시각으로 초기화된다.")
    void success_create() {
      // when
      Conversation conversation = Conversation.createConversation(userA, userB);

      // then
      assertThat(conversation.getUserA()).isEqualTo(userA);
      assertThat(conversation.getUserB()).isEqualTo(userB);
      assertThat(conversation.getLastMessageAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("상대방 판별(getPartnerOf) 테스트")
  class GetPartnerOf {

    @Test
    @DisplayName("성공: 요청자가 UserA이면 UserB를 반환한다.")
    void success_return_userB() {
      Conversation conversation = Conversation.createConversation(userA, userB);
      User partner = conversation.getPartnerOf(userAId);
      assertThat(partner).isEqualTo(userB);
    }

    @Test
    @DisplayName("성공: 요청자가 UserB이면 UserA를 반환한다.")
    void success_return_userA() {
      Conversation conversation = Conversation.createConversation(userA, userB);
      User partner = conversation.getPartnerOf(userBId);
      assertThat(partner).isEqualTo(userA);
    }

    @Test
    @DisplayName("실패: 참여자가 아닌 제3자가 요청하면 ACCESS_DENIED 예외가 발생한다.")
    void fail_unauthorized_user() {
      Conversation conversation = Conversation.createConversation(userA, userB);
      assertThatThrownBy(() -> conversation.getPartnerOf(strangerId))
          .isInstanceOf(ConversationException.class)
          .hasMessageContaining(ConversationErrorCode.ACCESS_DENIED.getMessage());
    }
  }

  @Nested
  @DisplayName("워터마크 갱신(updateLastReadAt) 및 조회(getMyLastReadAt) 테스트")
  class UpdateLastReadAt {

    @Test
    @DisplayName("성공: 기존 값이 null일 때 새로운 시각으로 정상 갱신된다.")
    void success_initial_update() {
      Conversation conversation = Conversation.createConversation(userA, userB);
      Instant now = Instant.now();

      conversation.updateLastReadAt(userAId, now);

      assertThat(conversation.getMyLastReadAt(userAId)).isEqualTo(now);
      assertThat(conversation.getMyLastReadAt(userBId)).isNull(); // 상대방은 여전히 null이어야 함
    }

    @Test
    @DisplayName("성공: 더 최신 시각(미래)이 들어오면 워터마크가 전진한다.")
    void success_forward_update() {
      Conversation conversation = Conversation.createConversation(userA, userB);
      Instant past = Instant.now().minusSeconds(60);
      Instant future = Instant.now();

      conversation.updateLastReadAt(userAId, past);
      conversation.updateLastReadAt(userAId, future);

      assertThat(conversation.getMyLastReadAt(userAId)).isEqualTo(future);
    }

    @Test
    @DisplayName("성공(방어): 과거 시각으로 갱신을 시도하면 무시되고 기존 최신 시각을 유지한다. (역행 방지)")
    void success_prevent_backward_update() {
      Conversation conversation = Conversation.createConversation(userA, userB);
      Instant future = Instant.now();
      Instant past = Instant.now().minusSeconds(60);

      conversation.updateLastReadAt(userAId, future);
      conversation.updateLastReadAt(userAId, past); // 과거 시각 주입 시도

      assertThat(conversation.getMyLastReadAt(userAId)).isEqualTo(future); // 역행하지 않고 future 유지
    }

    @Test
    @DisplayName("실패: 제3자가 읽음 시각 갱신 또는 조회를 시도하면 ACCESS_DENIED 예외가 발생한다.")
    void fail_unauthorized_update_and_get() {
      Conversation conversation = Conversation.createConversation(userA, userB);
      Instant now = Instant.now();

      assertThatThrownBy(() -> conversation.updateLastReadAt(strangerId, now))
          .isInstanceOf(ConversationException.class)
          .hasMessageContaining(ConversationErrorCode.ACCESS_DENIED.getMessage());

      assertThatThrownBy(() -> conversation.getMyLastReadAt(strangerId))
          .isInstanceOf(ConversationException.class)
          .hasMessageContaining(ConversationErrorCode.ACCESS_DENIED.getMessage());
    }
  }

  @Nested
  @DisplayName("마지막 메시지 캐싱(updateLastMessage) 테스트")
  class UpdateLastMessage {

    @Test
    @DisplayName("성공: 메시지 갱신 시 lastMessageAt 컬럼이 해당 메시지의 생성 시각으로 동기화된다.")
    void success_update_last_message() {
      Conversation conversation = Conversation.createConversation(userA, userB);
      DirectMessage mockMessage = mock(DirectMessage.class);
      Instant messageTime = Instant.now().plusSeconds(100);

      given(mockMessage.getCreatedAt()).willReturn(messageTime);

      conversation.updateLastMessage(mockMessage);

      assertThat(conversation.getLastMessage()).isEqualTo(mockMessage);
      assertThat(conversation.getLastMessageAt()).isEqualTo(messageTime);
    }
  }
}