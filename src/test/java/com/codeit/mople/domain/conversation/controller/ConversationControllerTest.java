package com.codeit.mople.domain.conversation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.conversation.dto.request.ConversationCreateRequest;
import com.codeit.mople.domain.conversation.dto.request.ConversationCursorRequest;
import com.codeit.mople.domain.conversation.dto.response.ConversationDto;
import com.codeit.mople.domain.conversation.dto.response.CursorResponseConversationDto;
import com.codeit.mople.domain.conversation.service.ConversationService;
import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.global.dto.UserSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConversationController.class)
public class ConversationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private ConversationService conversationService;

  private UsernamePasswordAuthenticationToken authToken;
  private UUID myUserId;

  @BeforeEach
  void setUp() {
    // @AuthenticationPrincipal 파라미터에 들어갈 가짜 유저 세팅
    myUserId = UUID.randomUUID();
    CustomUserDetails mockUser = new CustomUserDetails(myUserId, Role.USER);
    authToken = new UsernamePasswordAuthenticationToken(mockUser, null, mockUser.getAuthorities());
  }

  // DTO 조립용 헬퍼 메서드
  private ConversationDto createMockConversationDto(UUID conversationId, UUID partnerId) {
    UserSummary partnerSummary = new UserSummary(partnerId, "상대방닉네임", "profile.jpg");
    UserSummary mySummary = new UserSummary(myUserId, "내닉네임", "my-profile.jpg");

    DirectMessageDto latestMessageDto = new DirectMessageDto(
        UUID.randomUUID(),
        conversationId,
        Instant.now(),
        mySummary,
        partnerSummary,
        "마지막으로 보낸 테스트 메시지"
    );

    return new ConversationDto(
        conversationId,
        partnerSummary,
        latestMessageDto,
        true // hasUnread
    );
  }

  @Test
  @DisplayName("POST /api/conversations - 대화방 생성 시 실제 응답 JSON을 반환한다.")
  void createConversation_success() throws Exception {
    // given
    UUID targetUserId = UUID.randomUUID();
    UUID newConversationId = UUID.randomUUID();

    ConversationCreateRequest request = new ConversationCreateRequest(targetUserId);
    ConversationDto response = createMockConversationDto(newConversationId, targetUserId);

    given(conversationService.findOrCreateConversation(eq(myUserId), eq(targetUserId)))
        .willReturn(response);

    // when & then
    mockMvc.perform(post("/api/conversations")
            .with(authentication(authToken))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(newConversationId.toString()))
        .andExpect(jsonPath("$.with.name").value("상대방닉네임"))
        .andExpect(jsonPath("$.lastestMessage.content").value("마지막으로 보낸 테스트 메시지"));
  }

  @Test
  @DisplayName("GET /api/conversations - 대화방 목록 조회 시 실제 커서 응답 JSON을 반환한다.")
  void findConversations_success() throws Exception {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();

    ConversationDto itemDto = createMockConversationDto(conversationId, partnerId);

    CursorResponseConversationDto response = new CursorResponseConversationDto(
        List.of(itemDto),
        null,
        null,
        false,
        0,
        null,
        null
    );

    given(
        conversationService.getMyConversations(eq(myUserId), any(ConversationCursorRequest.class)))
        .willReturn(response);

    // when & then
    mockMvc.perform(get("/api/conversations")
            .with(authentication(authToken))
            .param("limit", "10")
            .param("keywordLike", "안녕"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(conversationId.toString()))
        .andExpect(jsonPath("$.data[0].with.name").value("상대방닉네임"))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  @DisplayName("GET /api/conversations/with - 특정 유저와의 대화방 조회 시 실제 응답 JSON을 반환한다.")
  void findConversationWithUser_success() throws Exception {
    // given
    UUID targetUserId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    ConversationDto response = createMockConversationDto(conversationId, targetUserId);

    given(conversationService.getConversationWithUser(eq(myUserId), eq(targetUserId)))
        .willReturn(response);

    // when & then
    mockMvc.perform(get("/api/conversations/with")
            .with(authentication(authToken))
            .param("userId", targetUserId.toString()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(conversationId.toString()))
        .andExpect(jsonPath("$.with.userId").value(targetUserId.toString()))
        .andExpect(jsonPath("$.hasUnread").value(true));
  }

  @Test
  @DisplayName("GET /api/conversations/{conversationId} - 대화방 식별자로 단건 조회 시 실제 응답 JSON을 반환한다.")
  void findConversation_success() throws Exception {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();

    ConversationDto response = createMockConversationDto(conversationId, partnerId);

    given(conversationService.getConversation(eq(myUserId), eq(conversationId)))
        .willReturn(response);

    // when & then
    mockMvc.perform(get("/api/conversations/{conversationId}", conversationId)
            .with(authentication(authToken)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(conversationId.toString()))
        .andExpect(jsonPath("$.lastestMessage.content").value("마지막으로 보낸 테스트 메시지"))
        .andExpect(jsonPath("$.with.profileImageUrl").value("profile.jpg"));
  }
}
