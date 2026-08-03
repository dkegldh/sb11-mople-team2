package com.codeit.mople.domain.conversation.service;

import com.codeit.mople.domain.conversation.dto.request.ConversationCursorRequest;
import com.codeit.mople.domain.conversation.dto.response.ConversationDto;
import com.codeit.mople.domain.conversation.dto.response.CursorResponseConversationDto;
import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.conversation.exception.ConversationErrorCode;
import com.codeit.mople.domain.conversation.exception.ConversationException;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

  private final UserRepository userRepository;
  private final ConversationRepository conversationRepository;

  @Transactional
  public ConversationDto findOrCreateConversation(UUID requesterId, UUID targetUserId) {
    log.debug("대화방 생성 시작 - requesterId: {}, targetUserId: {}", requesterId, targetUserId);

    if (requesterId.equals(targetUserId)) {
      throw new ConversationException(ConversationErrorCode.INVALID_PARTICIPANT, Map.of("requesterId", requesterId));
    }

    // userAId < userBId
    UUID userAId = (requesterId.compareTo(targetUserId) < 0) ? requesterId : targetUserId;
    UUID userBId = (userAId.equals(requesterId)) ? targetUserId : requesterId;

    User userA = userRepository.findById(userAId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND, Map.of("userId", userAId)));
    User userB = userRepository.findById(userBId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND, Map.of("userId", userBId)));

    Conversation conversation;
    try {
      conversation = conversationRepository.findByUserAAndUserB(userA, userB)
          .orElseGet(() -> {
            log.info("기존 대화방 없음, 새 대화방 생성 - userAId: {}, userBId: {}", userAId, userBId);
            return conversationRepository.saveAndFlush(Conversation.createConversation(userA, userB));
          });
    } catch (DataIntegrityViolationException e) {
      log.info("동시 대화방 생성 충돌 발생, 기존 방 재조회 시도 - userAId: {}, userBId: {}", userAId, userBId);
      conversation = conversationRepository.findByUserAAndUserB(userA, userB)
          .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND, Map.of("userAId", userAId, "userBId", userBId)));
    }

    log.info("대화방 생성 완료 - conversationId: {}", conversation.getId());
    return ConversationDto.from(conversation, requesterId);
  }

  public ConversationDto getConversation(UUID conversationId, UUID requesterId) {
    log.debug("대화방 단건 조회 요청 - conversationId: {}, requesterId: {}", conversationId, requesterId);

    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND, Map.of("conversationId", conversationId)));

    validateParticipant(conversation, requesterId);

    return ConversationDto.from(conversation, requesterId);
  }

  public ConversationDto getConversationWithUser(UUID requesterId, UUID targetUserId) {
    log.debug("상대 유저 지정을 통한 대화방 조회 요청 - requesterId: {}, targetUserId: {}", requesterId, targetUserId);

    UUID userAId = (requesterId.compareTo(targetUserId) < 0) ? requesterId : targetUserId;
    UUID userBId = (userAId.equals(requesterId)) ? targetUserId : requesterId;

    User userA = userRepository.findById(userAId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND, Map.of("userId", userAId)));

    User userB = userRepository.findById(userBId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND, Map.of("userId", userBId)));

    Conversation conversation = conversationRepository.findByUserAAndUserB(userA, userB)
        .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND, Map.of("userAId", userAId, "userBId", userBId)));

    return ConversationDto.from(conversation, requesterId);
  }

  public CursorResponseConversationDto getMyConversations(UUID requesterId, ConversationCursorRequest request) {
    log.debug("내 대화방 목록 조회 요청 - requesterId: {}, limit: {}, cursor: {}", requesterId, request.limit(), request.cursor());

    userRepository.findById(requesterId)
        .orElseThrow(() -> new ConversationException(UserErrorCode.USER_NOT_FOUND, Map.of("userId", requesterId)));

    Instant cursorTime = request.parseCursorToInstant();

    List<Conversation> conversations = conversationRepository.findConversationByCursor(requesterId, request, cursorTime);

    boolean hasNext = conversations.size() > request.limit();
    List<Conversation> slicedConversations = hasNext ? conversations.subList(0, request.limit()) : conversations;

    List<ConversationDto> conversationDtos = slicedConversations.stream()
        .map(c -> ConversationDto.from(c, requesterId))
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext && !slicedConversations.isEmpty()) {
      Conversation lastItem = slicedConversations.get(slicedConversations.size() - 1);

      // 대화방에 lastMessage가 없으면 대화방의 createdAt을 커서로 사용
      Instant nextTime = (lastItem.getLastMessage() != null && lastItem.getLastMessage().getCreatedAt() != null)
          ? lastItem.getLastMessage().getCreatedAt()
          : lastItem.getCreatedAt();

      if (nextTime != null) {
        nextCursor = nextTime.toString();
        nextIdAfter = lastItem.getId();
      }
    }

    return new CursorResponseConversationDto(
        conversationDtos,
        nextCursor,
        nextIdAfter,
        hasNext,
        conversationDtos.size(),
        request.sortBy(),
        request.sortDirection()
    );
  }

  private void validateParticipant(Conversation conversation, UUID requesterId) {
    if (!conversation.getUserA().getId().equals(requesterId) && !conversation.getUserB().getId()
        .equals(requesterId)) {
      log.warn("대화방 인가 실패, 권한 없는 유저의 접근 - conversationId: {}, requesterId: {}", conversation.getId(), requesterId);
      throw new ConversationException(ConversationErrorCode.ACCESS_DENIED, Map.of("conversationId", conversation.getId(), "requesterId", requesterId));
    }
  }
}
