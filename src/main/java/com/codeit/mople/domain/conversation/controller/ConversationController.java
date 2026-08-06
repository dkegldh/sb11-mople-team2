package com.codeit.mople.domain.conversation.controller;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.conversation.dto.request.ConversationCreateRequest;
import com.codeit.mople.domain.conversation.dto.request.ConversationCursorRequest;
import com.codeit.mople.domain.conversation.dto.response.ConversationDto;
import com.codeit.mople.domain.conversation.dto.response.CursorResponseConversationDto;
import com.codeit.mople.domain.conversation.service.ConversationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations")
public class ConversationController {

  private final ConversationService conversationService;

  @PostMapping
  public ResponseEntity<ConversationDto> createConversation(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody ConversationCreateRequest request
  ) {
    ConversationDto response = conversationService.findOrCreateConversation(userDetails.getUserId(), request.withUserId());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  public ResponseEntity<CursorResponseConversationDto> findConversations(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid ConversationCursorRequest request
  ) {
    CursorResponseConversationDto response = conversationService.getMyConversations(userDetails.getUserId(), request);
    return ResponseEntity.ok(response);
  }

  // 다른 사용자의 프로필에 들어가서 "메시지 보내기" 버튼을 눌렀을 때 호출
  @GetMapping("/with")
  public ResponseEntity<ConversationDto> findConversationWithUser(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam UUID userId
  ) {
    ConversationDto response = conversationService.getConversationWithUser(userDetails.getUserId(), userId);
    return ResponseEntity.ok(response);
  }

  // 사용자 대화방 목록 페이지에서 특정 대화방 목록을 클릭하여 진입했을 때 호출
  @GetMapping("/{conversationId}")
  public ResponseEntity<ConversationDto> findConversation(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable UUID conversationId
  ) {
    ConversationDto response = conversationService.getConversation(userDetails.getUserId(), conversationId);
    return ResponseEntity.ok(response);
  }
}
