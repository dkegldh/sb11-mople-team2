package com.codeit.mople.domain.directmessage.controller;

import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.dto.response.CursorResponseDirectMessageDto;
import com.codeit.mople.domain.directmessage.service.DirectMessageService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations/{conversationId}/direct-messages")
public class DirectMessageController {

  // TODO: 보안 연동 완료 시 @AuthenticationPrincipal로 교체
  private static final UUID TEMP_REQUESTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

  private final DirectMessageService directMessageService;

  @GetMapping
  public ResponseEntity<CursorResponseDirectMessageDto> getDirectMessages(
      @PathVariable UUID conversationId,
      @Valid DirectMessageCursorRequest request
  ) {
    CursorResponseDirectMessageDto response = directMessageService.getDirectMessages(conversationId, TEMP_REQUESTER_ID, request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{directMessageId}/read")
  public ResponseEntity<Void> readMessage(
      @PathVariable UUID conversationId,
      @PathVariable UUID directMessageId
  ) {
    directMessageService.readMessage(conversationId, directMessageId, TEMP_REQUESTER_ID);
    return ResponseEntity.noContent().build();
  }
}
