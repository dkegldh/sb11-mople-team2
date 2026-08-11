package com.codeit.mople.domain.directmessage.service;

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
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectMessageService {

  private final DirectMessageRepository directMessageRepository;
  private final ConversationRepository conversationRepository;

  private final ApplicationEventPublisher eventPublisher;

  // DM 발송 및 DB에 저장
  @Transactional
  public DirectMessageDto sendMessage(UUID conversationId, UUID senderId, String content) {
    log.debug("WebSocket DM 발송 및 영속화 시작 - conversationId: {}, senderId: {}", conversationId, senderId);

    // 비관적 락이 걸린 전용 메서드로 조회하여 동시 덮어쓰기(Lost Update) 원천 차단
    Conversation conversation = conversationRepository.findWithLockById(conversationId)
        .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND, Map.of("conversationId", conversationId)));

    validateConversationParticipant(conversation, senderId);

    User sender = conversation.getUserA().getId().equals(senderId) ? conversation.getUserA() : conversation.getUserB();
    User receiver = conversation.getPartnerOf(senderId);

    DirectMessage directMessage = DirectMessage.createMessage(conversation, sender, receiver, content);
    directMessage = directMessageRepository.save(directMessage);

    conversation.updateLastMessage(directMessage);
    // 안 읽은 메시지가 생기는 동시성 혼선 방지 - 발신자 자신의 워터마크를 해당 메시지의 생성 시각으로 강제 전진
    conversation.updateLastReadAt(senderId, directMessage.getCreatedAt());

    DirectMessageDto responseDto = DirectMessageDto.from(directMessage);

    eventPublisher.publishEvent(new DirectMessageCreatedEvent(receiver.getId(), directMessage.getId()));

    log.info("WebSocket DM 저장 및 워터마크/마지막 메시지 갱신 완료 - conversationId: {}, messageId: {}", conversationId, directMessage.getId());

    return responseDto;
  }

  // 특정 대화방의 메시지 목록 조회
  @Transactional
  public CursorResponseDirectMessageDto getDirectMessages(UUID conversationId, UUID requesterId,
      DirectMessageCursorRequest request) {
    log.debug("특정 DM 목록 조회 요청 - conversationId: {}, requesterId: {}", conversationId, requesterId);
    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND,
            Map.of("conversationId", conversationId)));

    validateConversationParticipant(conversation, requesterId);

    Instant cursorTime = request.parseCursorToInstant();
    List<DirectMessage> messages = directMessageRepository.findDirectMessageByCursor(conversationId,
        request, cursorTime);

    boolean hasNext = messages.size() > request.limit();
    List<DirectMessage> slicedMessages = hasNext ? messages.subList(0, request.limit()) : messages;

    // 스크롤 시마다 발생하는 UPDATE 오버헤드를 방지하기 위해 처음 채팅 방에 들어왔을 때 유저의 읽은 시각을 가장 최근 메시지의 생성 시각으로 업데이트
    if (cursorTime == null && !slicedMessages.isEmpty()) {
      conversation.updateLastReadAt(requesterId, slicedMessages.get(0).getCreatedAt());
      log.debug("대화방 최초 진입 감지, lastReadAt 워터마크 갱신 완료 - conversationId: {}", conversationId);
    }

    List<DirectMessageDto> directMessageDtos = slicedMessages.stream()
        .map(DirectMessageDto::from)
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext && !slicedMessages.isEmpty()) {
      DirectMessage lastItem = slicedMessages.get(slicedMessages.size() - 1);
      nextCursor = lastItem.getCreatedAt().toString();
      nextIdAfter = lastItem.getId();
    }

    log.info("특정 DM 목록 조회 완료 - conversationId: {}", conversationId);

    return new CursorResponseDirectMessageDto(
        directMessageDtos,
        nextCursor,
        nextIdAfter,
        hasNext,
        directMessageDtos.size(),
        request.sortBy(),
        request.sortDirection()
    );
  }

  // 단건 메시지 읽음 처리
  @Transactional
  public void readMessage(UUID conversationId, UUID directMessageId, UUID requesterId) {
    log.debug("단건 DM 읽음 처리 요청 - messageId: {}, requesterId: {}", directMessageId, requesterId);

    DirectMessage message = directMessageRepository.findById(directMessageId)
        .orElseThrow(
            () -> new DirectMessageException(DirectMessageErrorCode.DIRECT_MESSAGE_NOT_FOUND,
                Map.of("directMessageId", directMessageId)));

    if (!message.getConversation().getId().equals(conversationId)) {
      log.warn("대화방-DM 소속 불일치 - path conversationId: {}, actual conversationId: {}, messageId: {}",
          conversationId, message.getConversation().getId(), directMessageId);
      throw new DirectMessageException(DirectMessageErrorCode.DIRECT_MESSAGE_NOT_FOUND,
          Map.of("conversationId", conversationId,
              "directMessageId", directMessageId));
    }

    if (message.getSender().getId().equals(requesterId)) {
      return;
    }

    if (!message.getReceiver().getId().equals(requesterId)) {
      log.warn("수신자가 아닌 유저의 접근, DM 읽음 처리 인가 실패 - messageId: {}, requesterId: {}", directMessageId,
          requesterId);
      throw new DirectMessageException(DirectMessageErrorCode.UNAUTHORIZED_RECEIVER,
          Map.of("requesterId", requesterId,
              "directMessageId", directMessageId));
    }

    Conversation conversation = message.getConversation();
    Instant myLastReadAt = conversation.getMyLastReadAt(requesterId);

    if (myLastReadAt != null && !message.getCreatedAt().isAfter(myLastReadAt)) {
      log.debug("이미 읽은 메시지이므로 추가 작업 생략 - messageId: {}", directMessageId);
      return;
    }

    conversation.updateLastReadAt(requesterId, message.getCreatedAt());

    log.info("DM 읽음 처리 완료 - messageId: {}", directMessageId);
  }

  // 공통 인가 로직 분리
  private void validateConversationParticipant(Conversation conversation, UUID requesterId) {
    if (!conversation.getUserA().getId().equals(requesterId) &&
        !conversation.getUserB().getId().equals(requesterId)) {
      throw new ConversationException(ConversationErrorCode.ACCESS_DENIED,
          Map.of("conversationId", conversation.getId(), "requesterId", requesterId));
    }
  }
}
