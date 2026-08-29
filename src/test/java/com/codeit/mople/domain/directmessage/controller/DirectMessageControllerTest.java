package com.codeit.mople.domain.directmessage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.dto.request.DirectMessageSendRequest;
import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.service.DirectMessageService;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.global.dto.CursorResponse;
import com.codeit.mople.global.dto.UserSummary;
import com.codeit.mople.global.error.DiscordWebhookService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DirectMessageController.class)
public class DirectMessageControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DirectMessageController directMessageController;

  @MockitoBean
  private DirectMessageService directMessageService;

  @MockitoBean
  private SimpMessagingTemplate messagingTemplate;

  @MockitoBean
  private MeterRegistry meterRegistry;

  @MockitoBean
  private DiscordWebhookService discordWebhookService;

  private UsernamePasswordAuthenticationToken authToken;
  private UUID myUserId;

  @BeforeEach
  void setUp() {
    myUserId = UUID.randomUUID();
    CustomUserDetails mockUser = new CustomUserDetails(myUserId, Role.USER);
    authToken = new UsernamePasswordAuthenticationToken(mockUser, null, mockUser.getAuthorities());
  }

  // DTO 생성을 위한 헬퍼 메서드
  private DirectMessageDto createMockDirectMessageDto(UUID conversationId, UUID messageId) {
    UserSummary sender = new UserSummary(myUserId, "내닉네임", "my-profile.jpg");
    UserSummary receiver = new UserSummary(UUID.randomUUID(), "상대방닉네임", "partner-profile.jpg");

    return new DirectMessageDto(
        messageId,
        conversationId,
        Instant.now(),
        sender,
        receiver,
        "테스트 다이렉트 메시지"
    );
  }

  @Test
  @DisplayName("STOMP 송신 - 메시지 전송 시 Service를 거쳐 특정 Destination으로 발행된다.")
  void sendDirectMessage_success() {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    String content = "테스트 웹소켓 메시지";

    DirectMessageSendRequest request = new DirectMessageSendRequest(content);

    DirectMessageDto mockResponseDto = createMockDirectMessageDto(conversationId, messageId);

    given(directMessageService.sendMessage(conversationId, myUserId, content))
        .willReturn(mockResponseDto);

    CustomUserDetails userDetails = (CustomUserDetails) authToken.getPrincipal();

    // when
    // 웹소켓(@MessageMapping)은 HTTP 통신이 아니므로 MockMvc 대신 컨트롤러 메서드를 직접 호출
    directMessageController.sendDirectMessage(conversationId, request, userDetails);

    // then
    then(directMessageService).should().sendMessage(conversationId, myUserId, content);

    // 올바른 목적지(destination)로 발송되었는지 검증
    String expectedDestination = "/sub/conversations/" + conversationId + "/direct-messages";
    then(messagingTemplate).should().convertAndSend(expectedDestination, mockResponseDto);
  }

  @Test
  @DisplayName("GET /api/conversations/{conversationId}/direct-messages - DM 목록 조회 시 실제 커서 응답 JSON을 반환한다.")
  void getDirectMessages_success() throws Exception {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();

    DirectMessageDto messageDto = createMockDirectMessageDto(conversationId, messageId);

    CursorResponse<DirectMessageDto> realResponse = new CursorResponse<>(
        List.of(messageDto),
        null,
        null,
        false,
        0,
        null,
        null
    );

    given(directMessageService.getDirectMessages(eq(conversationId), eq(myUserId), any(
        DirectMessageCursorRequest.class)))
        .willReturn(realResponse);

    // when & then
    mockMvc.perform(get("/api/conversations/{conversationId}/direct-messages", conversationId)
            .with(authentication(authToken))
            .param("limit", "20"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(messageId.toString()))
        .andExpect(jsonPath("$.data[0].conversationId").value(conversationId.toString()))
        .andExpect(jsonPath("$.data[0].content").value("테스트 다이렉트 메시지"))
        .andExpect(jsonPath("$.data[0].sender.name").value("내닉네임"))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  @DisplayName("POST /api/conversations/{conversationId}/direct-messages/{directMessageId}/read - 메시지 읽음 처리 시 204 No Content를 반환한다.")
  void readMessage_success() throws Exception {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();

    // when & then
    mockMvc.perform(
            post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                conversationId, messageId)
                .with(authentication(authToken))
                .with(csrf()))
        .andDo(print())
        .andExpect(status().isNoContent());
    then(directMessageService).should().readMessage(conversationId, messageId, myUserId);
  }
}
