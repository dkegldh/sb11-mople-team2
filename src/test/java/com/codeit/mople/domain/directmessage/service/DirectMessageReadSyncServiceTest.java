package com.codeit.mople.domain.directmessage.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DirectMessageReadSyncServiceTest {

  @InjectMocks
  private DirectMessageReadSyncService directMessageReadSyncService;

  @Mock
  private ConversationRepository conversationRepository;

  @Test
  @DisplayName("성공: 존재하는 대화방일 경우 유저의 마지막 읽음 시각을 정상적으로 갱신한다.")
  void success_syncToDb() {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant lastReadAt = Instant.now();

    Conversation mockConversation = mock(Conversation.class);
    given(conversationRepository.findById(conversationId)).willReturn(Optional.of(mockConversation));

    // when
    directMessageReadSyncService.syncToDb(conversationId, userId, lastReadAt);

    // then
    verify(conversationRepository).findById(conversationId);
    verify(mockConversation).updateLastReadAt(userId, lastReadAt);
  }

  @Test
  @DisplayName("성공 (무시): 존재하지 않는 대화방(또는 삭제된 대화방)일 경우 예외 없이 조용히 종료된다.")
  void ignore_when_conversation_not_found() {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant lastReadAt = Instant.now();

    given(conversationRepository.findById(conversationId)).willReturn(Optional.empty());

    // when
    directMessageReadSyncService.syncToDb(conversationId, userId, lastReadAt);

    // then
    verify(conversationRepository).findById(conversationId);
    verifyNoMoreInteractions(conversationRepository);
  }
}
