package com.codeit.mople.domain.directmessage.controller;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.dto.response.CursorResponseDirectMessageDto;
import com.codeit.mople.domain.directmessage.service.DirectMessageService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations/{conversationId}/direct-messages")
public class DirectMessageController {

  private final DirectMessageService directMessageService;

  @GetMapping
  public ResponseEntity<CursorResponseDirectMessageDto> getDirectMessages(
      @PathVariable UUID conversationId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid DirectMessageCursorRequest request
  ) {
    CursorResponseDirectMessageDto response = directMessageService.getDirectMessages(conversationId, userDetails.getUserId(), request);
    return ResponseEntity.ok(response);
  }

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
