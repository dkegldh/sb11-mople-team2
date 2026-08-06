package com.codeit.mople.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.conversation.dto.request.ConversationCursorRequest;
import com.codeit.mople.domain.conversation.dto.response.ConversationDto;
import com.codeit.mople.domain.conversation.dto.response.CursorResponseConversationDto;
import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.conversation.exception.ConversationErrorCode;
import com.codeit.mople.domain.conversation.exception.ConversationException;
import com.codeit.mople.domain.conversation.mapper.ConversationMapper;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.dto.UserSummary;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ConversationServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private ConversationRepository conversationRepository;

  @Mock
  private ConversationMapper conversationMapper;

  @InjectMocks
  private ConversationService conversationService;

  private User userA;
  private User userB;
  private UUID userAId;
  private UUID userBId;

  @BeforeEach
  void setUp() {
    userAId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    userBId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    userA = mock(User.class);
    userB = mock(User.class);
  }

  @Nested
  @DisplayName("findOrCreateConversation 테스트")
  class FindOrCreateConversation {

    @Test
    @DisplayName("성공: 기존 대화방이 없으면 사용자 정렬 순서 보장하여 새로 생성한다.")
    void success_create_new_conversation() {
      //given
      given(userRepository.findById(userAId)).willReturn(Optional.of(userA));
      given(userRepository.findById(userBId)).willReturn(Optional.of(userB));
      given(conversationRepository.findWithDetailsByUserAAndUserB(userA, userB)).willReturn(Optional.empty());

      Conversation newConversation = Conversation.createConversation(userA, userB);
      UUID newConversationId = UUID.randomUUID();
      ReflectionTestUtils.setField(newConversation, "id", newConversationId);
      given(conversationRepository.saveAndFlush(any(Conversation.class))).willReturn(newConversation);

      UserSummary dummySummary = new UserSummary(userAId, "dummyName", "dummyUrl");
      ConversationDto dummyDto = new ConversationDto(newConversationId, dummySummary, null, false);
      given(conversationMapper.toDto(newConversation, userBId)).willReturn(dummyDto);

      //when - userB가 userA에게 요청
      ConversationDto result = conversationService.findOrCreateConversation(userBId, userAId);

      //then
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(newConversationId);
      verify(conversationRepository, times(1)).saveAndFlush(any(Conversation.class));
      verify(conversationMapper, times(1)).toDto(newConversation, userBId);
    }

    @Test
    @DisplayName("성공: 기존 대화방이 존재하면 생성하지 않고 기존 방을 반환한다.")
    void success_return_existing_conversation(){
      //given
      given(userRepository.findById(userAId)).willReturn(Optional.of(userA));
      given(userRepository.findById(userBId)).willReturn(Optional.of(userB));

      Conversation existingConversation = Conversation.createConversation(userA, userB);
      ReflectionTestUtils.setField(existingConversation, "id", UUID.randomUUID());
      given(conversationRepository.findWithDetailsByUserAAndUserB(userA, userB)).willReturn(Optional.of(existingConversation));

      ConversationDto dummyDto = new ConversationDto(existingConversation.getId(), null, null, false);
      given(conversationMapper.toDto(existingConversation, userAId)).willReturn(dummyDto);

      //when
      ConversationDto result = conversationService.findOrCreateConversation(userAId, userBId);

      //then
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(existingConversation.getId());
      verify(conversationRepository, never()).saveAndFlush(any(Conversation.class));
    }

    @Test
    @DisplayName("실패: 자기 자신과 대화를 시도하면 INVALID_PARTICIPANT 예외를 던진다.")
    void fail_self_conversation() {
      // when & then
      assertThatThrownBy(() -> conversationService.findOrCreateConversation(userAId, userAId))
          .isInstanceOf(ConversationException.class)
          .hasMessageContaining(ConversationErrorCode.INVALID_PARTICIPANT.getMessage());
    }
  }
  @Nested
  @DisplayName("getConversationWithUser 상대방 ID 기반 조회 테스트")
  class GetConversationWithUser {

    @Test
    @DisplayName("성공: 지정한 상대방과의 대화방이 존재하면 정상 반환한다.")
    void success_get_conversation_with_user() {
      //given
      given(userRepository.findById(userAId)).willReturn(Optional.of(userA));
      given(userRepository.findById(userBId)).willReturn(Optional.of(userB));

      Conversation conversation = Conversation.createConversation(userA, userB);
      UUID conversationId = UUID.randomUUID();
      ReflectionTestUtils.setField(conversation, "id", conversationId);

      given(conversationRepository.findWithDetailsByUserAAndUserB(userA, userB)).willReturn(Optional.of(conversation));

      ConversationDto dummyDto = new ConversationDto(conversationId, null, null, false);
      given(conversationMapper.toDto(any(Conversation.class), eq(userAId))).willReturn(dummyDto);

      //when
      ConversationDto result = conversationService.getConversationWithUser(userAId, userBId);

      //then
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(conversationId);
    }

    @Test
    @DisplayName("실패: 지정한 상대방과의 대화방이 존재하지 않으면 CONVERSATION_NOT_FOUND 예외를 던진다.")
    void fail_conversation_not_found() {
      //given
      given(userRepository.findById(userAId)).willReturn(Optional.of(userA));
      given(userRepository.findById(userBId)).willReturn(Optional.of(userB));
      given(conversationRepository.findWithDetailsByUserAAndUserB(userA, userB)).willReturn(Optional.empty());

      //when & then
      assertThatThrownBy(() -> conversationService.getConversationWithUser(userAId, userBId))
          .isInstanceOf(ConversationException.class)
          .hasMessageContaining(ConversationErrorCode.CONVERSATION_NOT_FOUND.getMessage());
    }
  }

  @Nested
  @DisplayName("getConversation 단건 조회 테스트")
  class GetConversation {

    @Test
    @DisplayName("성공: 방 참여자가 조회 시 정상 반환한다.")
    void success_get_conversation_participant() {
      //given
      given(userA.getId()).willReturn(userAId);

      Conversation conversation = Conversation.createConversation(userA, userB);
      UUID conversationId = UUID.randomUUID();
      ReflectionTestUtils.setField(conversation, "id", conversationId);

      given(conversationRepository.findWithDetailsById(conversationId)).willReturn(Optional.of(conversation));

      ConversationDto dummyDto = new ConversationDto(conversationId, null, null, false);
      given(conversationMapper.toDto(any(Conversation.class), eq(userAId))).willReturn(dummyDto);

      //when
      ConversationDto result = conversationService.getConversation(userAId, conversationId);

      //then
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(conversationId);
    }

    @Test
    @DisplayName("실패: 방 참여자가 아닌 유저가 조회 시 ACCESS_DENIED 예외를 던진다.")
    void fail_get_conversation_not_participant() {
      //given
      given(userA.getId()).willReturn(userAId);
      given(userB.getId()).willReturn(userBId);

      Conversation conversation = Conversation.createConversation(userA, userB);
      UUID conversationId = UUID.randomUUID();
      ReflectionTestUtils.setField(conversation, "id", conversationId);

      UUID strangerId = UUID.fromString("00000000-0000-0000-0000-000000000009");

      given(conversationRepository.findWithDetailsById(conversationId)).willReturn(Optional.of(conversation));

      //when & then
      assertThatThrownBy(() -> conversationService.getConversation(strangerId, conversationId))
          .isInstanceOf(ConversationException.class)
          .hasMessageContaining(ConversationErrorCode.ACCESS_DENIED.getMessage());
    }
  }

  @Nested
  @DisplayName("getMyConversations 목록 조회 테스트")
  class GetMyConversations {

    @Test
    @DisplayName("성공: 본인이 속한 모든 대화 목록을 최신순 커서 페이징으로 가져온다.")
    void success_get_my_conversations() {
      //given
      given(userRepository.findById(userAId)).willReturn(Optional.of(userA));

      Conversation conversation = Conversation.createConversation(userA, userB);
      DirectMessage mockMessage = mock(DirectMessage.class);
      conversation.updateLastMessage(mockMessage);

      ConversationCursorRequest mockRequest = mock(ConversationCursorRequest.class);
      given(mockRequest.limit()).willReturn(20);
      given(mockRequest.sortBy()).willReturn("createdAt");
      given(mockRequest.sortDirection()).willReturn("DESCENDING");
      given(mockRequest.parseCursorToInstant()).willReturn(null);

      given(conversationRepository.findConversationByCursor(eq(userAId), eq(mockRequest), any()))
          .willReturn(List.of(conversation));

      ConversationDto dummyDto = new ConversationDto(UUID.randomUUID(), null, null, false);
      given(conversationMapper.toDto(any(Conversation.class), eq(userAId))).willReturn(dummyDto);

      //when
      CursorResponseConversationDto result = conversationService.getMyConversations(userAId, mockRequest);

      //then
      assertThat(result).isNotNull();
      assertThat(result.data()).hasSize(1);
      assertThat(result.sortDirection()).isEqualTo("DESCENDING");
    }

    @Test
    @DisplayName("성공: 마지막 메시지가 없는 빈 대화방이 마지막 항목일 때 lastMessageAt을 nextCursor로 사용한다.")
    void success_get_my_conversations_with_empty_conversation_fallback() {
      //given
      given(userRepository.findById(userAId)).willReturn(Optional.of(userA));

      Conversation emptyConversation = Conversation.createConversation(userA, userB);
      UUID conversationId = UUID.randomUUID();
      Instant lastMessageAtTime = Instant.now().minusSeconds(60);

      ReflectionTestUtils.setField(emptyConversation, "id", conversationId);
      ReflectionTestUtils.setField(emptyConversation, "lastMessageAt", lastMessageAtTime);

      ConversationCursorRequest mockRequest = mock(ConversationCursorRequest.class);
      given(mockRequest.limit()).willReturn(1);
      given(mockRequest.sortBy()).willReturn("createdAt");
      given(mockRequest.sortDirection()).willReturn("DESCENDING");
      given(mockRequest.parseCursorToInstant()).willReturn(null);

      Conversation dummyConversation = Conversation.createConversation(userA, userB);
      given(conversationRepository.findConversationByCursor(eq(userAId), eq(mockRequest), any()))
          .willReturn(List.of(emptyConversation, dummyConversation));

      ConversationDto dummyDto = new ConversationDto(UUID.randomUUID(), null, null, false);
      given(conversationMapper.toDto(any(Conversation.class), eq(userAId))).willReturn(dummyDto);

      //when
      CursorResponseConversationDto result = conversationService.getMyConversations(userAId, mockRequest);

      //then
      assertThat(result).isNotNull();
      assertThat(result.hasNext()).isTrue();
      assertThat(result.data()).hasSize(1);

      assertThat(result.nextCursor()).isEqualTo(lastMessageAtTime.toString());
      assertThat(result.nextIdAfter()).isEqualTo(conversationId);
    }
  }
}