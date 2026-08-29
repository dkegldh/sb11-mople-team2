package com.codeit.mople.domain.directmessage.service;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.conversation.exception.ConversationErrorCode;
import com.codeit.mople.domain.conversation.exception.ConversationException;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.directmessage.event.DirectMessageLastReadAtEvent;
import com.codeit.mople.domain.directmessage.repository.DirectMessageReadRedisRepository;
import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.directmessage.event.DirectMessageCreatedEvent;
import com.codeit.mople.domain.directmessage.exception.DirectMessageErrorCode;
import com.codeit.mople.domain.directmessage.exception.DirectMessageException;
import com.codeit.mople.domain.directmessage.event.DirectMessageReceivedEvent;
import com.codeit.mople.domain.directmessage.repository.DirectMessageRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.dto.CursorResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final ApplicationEventPublisher publisher;
  private final DirectMessageReadRedisRepository readRedisRepository;

  private final MeterRegistry meterRegistry;
  private Counter dmSendCounter;
  private Counter dmReadCounter;

  @PostConstruct
  public void initMetrics() {
    this.dmSendCounter = Counter.builder("mople.dm.send.count")
        .description("DM 메시지 발송 횟수")
        .register(meterRegistry);

    this.dmReadCounter = Counter.builder("mople.dm.read.count")
        .description("DM 메시지 읽음 처리 횟수")
        .register(meterRegistry);
  }

  // DM 발송 및 DB에 저장
  @Transactional
  public DirectMessageDto sendMessage(UUID conversationId, UUID senderId, String content) {
    log.debug("WebSocket DM 발송 및 영속화 시작 - conversationId: {}, senderId: {}", conversationId, senderId);

    Conversation conversation = conversationRepository.findByIdWithUsers(conversationId)
        .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND, Map.of("conversationId", conversationId)));

    validateConversationParticipant(conversation, senderId);

    User sender = conversation.getUserA().getId().equals(senderId) ? conversation.getUserA() : conversation.getUserB();
    User receiver = conversation.getPartnerOf(senderId);

    DirectMessage directMessage = DirectMessage.createMessage(conversation, sender, receiver, content);
    directMessage = directMessageRepository.save(directMessage);

    conversation.updateLastMessage(directMessage);

    // 안 읽은 메시지가 생기는 동시성 혼선 방지 - 발신자 자신의 워터마크를 해당 메시지의 생성 시각으로 강제 전진
    publisher.publishEvent(new DirectMessageLastReadAtEvent(conversationId, senderId, directMessage.getCreatedAt()));

    publisher.publishEvent(new DirectMessageReceivedEvent(receiver.getId(), sender.getName(), content));

    DirectMessageDto responseDto = DirectMessageDto.from(directMessage);

    publisher.publishEvent(new DirectMessageCreatedEvent(UUID.randomUUID(), directMessage.getCreatedAt(), receiver.getId(), directMessage.getId()));

    //발송 성공 시 카운트 증가
    dmSendCounter.increment();

    log.info("WebSocket DM 저장 및 워터마크/마지막 메시지 갱신 완료 - conversationId: {}, messageId: {}", conversationId, directMessage.getId());

    return responseDto;
  }

  // 특정 대화방의 메시지 목록 조회
  @Transactional
  public CursorResponse<DirectMessageDto> getDirectMessages(UUID conversationId, UUID requesterId,
      DirectMessageCursorRequest request) {
    log.debug("특정 DM 목록 조회 요청 - conversationId: {}, requesterId: {}", conversationId, requesterId);
    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND,
            Map.of("conversationId", conversationId)));

    validateConversationParticipant(conversation, requesterId);

    long totalCount = directMessageRepository.countByConversationId(conversationId);

    Instant cursorTime = request.parseCursorToInstant();
    List<DirectMessage> messages = directMessageRepository.findDirectMessageByCursor(conversationId,
        request, cursorTime);

    if (cursorTime == null && !messages.isEmpty()) {
      updateLastReadAt(conversation, requesterId, messages.get(0).getCreatedAt());
      log.debug("대화방 최초 진입 감지, 레디스 lastReadAt 워터마크 갱신 완료 - conversationId: {}", conversationId);
    }

    List<DirectMessageDto> directMessageDtos = messages.stream()
        .map(DirectMessageDto::from)
        .toList();

    log.info("특정 DM 목록 조회 완료 - conversationId: {}", conversationId);

    return CursorResponse.of(
        directMessageDtos,
        request.limit(),
        totalCount,
        request.sortBy(),
        request.sortDirection(),
        dto -> dto.createdAt().toString(),
        DirectMessageDto::id
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

    // 최신 읽음 시각 조회 시 레디스부터 확인 후 DB 조회 후 레디스에 복구
    Instant myLastReadAt;
    Optional<Instant> cachedValue = readRedisRepository.getCachedLastReadAt(conversationId, requesterId);

    if (cachedValue.isPresent()) {
      myLastReadAt = cachedValue.get();
    } else {
      log.info("Redis Cache Miss: DB에서 직접 읽음 시각 조회 진행 - conversationId: {}, requesterId: {}", conversationId, requesterId);
      myLastReadAt = conversation.getMyLastReadAt(requesterId);
      if (myLastReadAt != null) {
        readRedisRepository.setCachedLastReadAt(conversationId, requesterId, myLastReadAt);
        log.debug("DB 조회 결과로 Redis 캐시 복구 완료 - conversationId: {}", conversationId);
      }
    }

    if (myLastReadAt != null && !message.getCreatedAt().isAfter(myLastReadAt)) {
      log.debug("조기 종료: 이미 읽은 메시지이므로 Redis 추가 갱신 생략 - messageId: {}", directMessageId);
      return;
    }

    updateLastReadAt(conversation, requesterId, message.getCreatedAt());

    //읽음 처리 성공 시 카운트 증가
    dmReadCounter.increment();
  }

  private void updateLastReadAt(Conversation conversation, UUID userId, Instant readAt) {
    boolean isRedisAlive = readRedisRepository.saveLastReadAt(conversation.getId(), userId, readAt);

    if (!isRedisAlive) {
      conversation.updateLastReadAt(userId, readAt);
      log.error("Redis 장애 감지: DB에 직접 읽음 시각 업데이트 (Fallback) 완료 - conversationId: {}", conversation.getId());
    } else {
      log.info("Redis 갱신: DM 읽음 시각 갱신 완료 - conversationId: {}", conversation.getId());
    }
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
