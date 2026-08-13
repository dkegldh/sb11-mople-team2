package com.codeit.mople.domain.directmessage.controller;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.directmessage.controller.api.DirectMessageApi;
import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.dto.request.DirectMessageSendRequest;
import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.service.DirectMessageService;
import com.codeit.mople.global.dto.CursorResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations/{conversationId}/direct-messages")
public class DirectMessageController implements DirectMessageApi {

  private final DirectMessageService directMessageService;
  private final SimpMessagingTemplate messagingTemplate;

  @MessageMapping("/conversations/{conversationId}/direct-messages")
  public void sendDirectMessage(
      @DestinationVariable UUID conversationId,
      @Valid DirectMessageSendRequest request,
      Principal principal
  ) {
    if (principal == null) {
      log.error("WebSocket SEND 실패, 인증되지 않은 사용자의 접근");
      throw new AuthException(AuthErrorCode.INVALID_TOKEN, Map.of("reason", "인증 정보가 없습니다."));
    }

    UsernamePasswordAuthenticationToken authToken = (UsernamePasswordAuthenticationToken) principal;
    CustomUserDetails userDetails = (CustomUserDetails) authToken.getPrincipal();

    UUID senderId = userDetails.getUserId();

    DirectMessageDto responseDto = directMessageService.sendMessage(conversationId, senderId, request.content());

    String destination = "/sub/conversations/" + conversationId + "/direct-messages";
    messagingTemplate.convertAndSend(destination, responseDto);
  }

  @Override
  @GetMapping
  public ResponseEntity<CursorResponse<DirectMessageDto>> getDirectMessages(
      @PathVariable UUID conversationId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid DirectMessageCursorRequest request
  ) {
    CursorResponse<DirectMessageDto> response = directMessageService.getDirectMessages(conversationId, userDetails.getUserId(), request);
    return ResponseEntity.ok(response);
  }

  @Override
  @PostMapping("/{directMessageId}/read")
  public ResponseEntity<Void> readMessage(
      @PathVariable UUID conversationId,
      @PathVariable UUID directMessageId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    directMessageService.readMessage(conversationId, directMessageId, userDetails.getUserId());
    return ResponseEntity.noContent().build();
  }
}
