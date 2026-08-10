package com.codeit.mople.domain.watchingsession.controller;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.domain.watchingsession.dto.ContentChatDto;
import com.codeit.mople.domain.watchingsession.dto.ContentChatSendRequest;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ContentWebSocketController {

  private final SimpMessagingTemplate messagingTemplate;
  private final UserRepository userRepository;


  //클라이언트가 SEND /pub/contents/{contentId}/chat 으로 메시지를 보내면 이 메서드가 실행
  //WebSocketConfig에서 설정한 ApplicationDestinationPrefixes에 의해 /pub는 생략되어 매핑
  @MessageMapping("/contents/{contentId}/chat")
  public void sendMessage(
      @DestinationVariable("contentId") UUID contentId,
      ContentChatSendRequest request,
      Principal principal
  ) {
    if (principal == null) {
      return;
    }

    //안전한 타입 검사 및 변환(instanceof 패턴 매칭)
    if (principal instanceof UsernamePasswordAuthenticationToken authentication &&
        authentication.getPrincipal() instanceof CustomUserDetails userDetails) {

      UUID senderId = userDetails.getUserId();

      //서버 측 DB에서 유저 이름 조회
      String senderName = userRepository.findById(senderId)
          .map(User::getName)
          .orElse("알 수 없는 유저");

      ContentChatDto response = new ContentChatDto(
          contentId.toString(),
          senderId,
          senderName,
          request.message(),
          Instant.now()
      );

      messagingTemplate.convertAndSend("/sub/contents/" + contentId + "/chat", response);
    } else {
      //인증 객체 타입이 맞지 않거나 CustomUserDetails가 아닌 경우 조용히 무시(또는 에러 로깅)
      return;
    }
  }
}
