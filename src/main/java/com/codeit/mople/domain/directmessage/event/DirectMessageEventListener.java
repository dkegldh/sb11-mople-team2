package com.codeit.mople.domain.directmessage.event;

import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.directmessage.exception.DirectMessageErrorCode;
import com.codeit.mople.domain.directmessage.exception.DirectMessageException;
import com.codeit.mople.domain.directmessage.repository.DirectMessageRepository;
import com.codeit.mople.global.sse.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectMessageEventListener {

  private final SseService sseService;
  private final DirectMessageRepository directMessageRepository;

  // Lazy 연관관계(User 2개)가 있기 때문에 영속성 컨텍스트를 거쳐야 할 필요가 있음
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(DirectMessageCreatedEvent event) {
    log.debug("SSE 이벤트 전송 시도: receiverId={}, directMessageId={}",
        event.receiverId(), event.directMessageId());

    DirectMessage directMessage =
        directMessageRepository.findById(event.directMessageId()).orElseThrow(() ->
            new DirectMessageException(DirectMessageErrorCode.DIRECT_MESSAGE_NOT_FOUND));

    DirectMessageDto directMessageDto = DirectMessageDto.from(directMessage);

    // TODO 김명근: kafka 도입 이후 topic 만들어서 비동기 처리(현재는 동기 상태)
    // SSE 이벤트 전송
    // RuntimeException, CustomException, IOException 등 다양한 예외가 발생할 수 있기 때문에 Exception으로 설정
    // 대신 로그 메시지에 stackTrace를 확인하여 어떤 예외가 발생했는지 알 수 있도록 설정
    try {
      sseService.send(
          event.receiverId(),
          "direct-messages",
          directMessageDto
      );

      log.info("SSE 전송 완료: receiverId={}, directMessageId={}",
          event.receiverId(), event.directMessageId());
    } catch (Exception e) {
      log.error("SSE 전송 실패: receiverId={}, directMessageId={}",
          event.receiverId(), event.directMessageId(), e);
    }

  }
}
