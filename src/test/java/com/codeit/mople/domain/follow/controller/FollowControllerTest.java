package com.codeit.mople.domain.follow.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.repository.AccountLockRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.auth.security.CustomOAuth2UserService;
import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.auth.security.handler.OAuth2FailureHandler;
import com.codeit.mople.domain.auth.security.handler.OAuth2SuccessHandler;
import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.dto.FollowResponse;
import com.codeit.mople.domain.follow.exception.FollowErrorCode;
import com.codeit.mople.domain.follow.exception.FollowException;
import com.codeit.mople.domain.follow.service.FollowService;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.global.config.SecurityConfig;
import com.codeit.mople.global.error.DiscordWebhookService;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FollowController.class)
@Import(SecurityConfig.class)
class FollowControllerTest {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @MockitoBean
  FollowService followService;
  
  @MockitoBean
  JwtProvider jwtProvider;

  @MockitoBean
  AccountLockRepository accountLockRepository;

  @MockitoBean
  SessionTokenRepository sessionTokenRepository;

  @MockitoBean
  CustomOAuth2UserService customOAuth2UserService;

  @MockitoBean
  OAuth2SuccessHandler oAuth2SuccessHandler;

  @MockitoBean
  OAuth2FailureHandler oAuth2FailureHandler;

  @MockitoBean
  private MeterRegistry meterRegistry;

  @MockitoBean
  private DiscordWebhookService discordWebhookService;

  CustomUserDetails principal;
  UUID followeeId;
  UUID followerId;
  UUID followId;

  @BeforeEach
  void setUp() {
    followeeId = UUID.randomUUID();
    followerId = UUID.randomUUID();
    followId = UUID.randomUUID();
    principal = new CustomUserDetails(followerId, Role.USER);
  }

  @Nested
  @DisplayName("팔로우 생성 [POST /api/follows]")
  class CreateFollow {

    @Test
    @DisplayName("팔로우 생성에 성공하면 201과 생성된 팔로우를 반환")
    void createFollowSuccess() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followeeId);
      given(followService.follow(any(FollowRequest.class), eq(followerId)))
          .willReturn(new FollowResponse(followId, followeeId, followerId));

      // when, then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(followId.toString()))
          .andExpect(jsonPath("$.followeeId").value(followeeId.toString()))
          .andExpect(jsonPath("$.followerId").value(followerId.toString()));
      
      verify(followService).follow(request, followerId);
    }

    @Test
    @DisplayName("followeeId 없이 요청하면 400을 반환")
    void createFollowFailWhenFolloweeIdIsNull() throws Exception {
      // given
      String request = "{}";

      // when, then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(request))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("COMMON-001"))
          .andExpect(jsonPath("$.error.message").value(containsString("followeeId")));

      verifyNoInteractions(followService);
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환")
    void createFollowFailWhenUnauthenticated() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followeeId);

      // when, then
      mockMvc.perform(post("/api/follows")
              .with(csrf())
              .accept(MediaType.APPLICATION_JSON)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(followService);
    }
    
    @Test
    @DisplayName("자기 자신을 팔로우하면 400을 반환")
    void createFollowFailWhenSelfFollow() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followerId);
      given(followService.follow(any(FollowRequest.class), eq(followerId)))
          .willThrow(new FollowException(FollowErrorCode.FOLLOW_SELF_NOT_ALLOWED));

      // when, then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-001"));
    }

    @Test
    @DisplayName("팔로우 대상이 없으면 400을 반환")
    void createFollowFailWhenFolloweeNotFound() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followeeId);
      given(followService.follow(any(FollowRequest.class), eq(followerId)))
          .willThrow(new FollowException(FollowErrorCode.FOLLOW_FOLLOWEE_NOT_FOUND));

      // when, then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-003"));
    }

    @Test
    @DisplayName("이미 팔로우 중이면 400을 반환")
    void createFollowFailWhenDuplicate() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followeeId);
      given(followService.follow(any(FollowRequest.class), eq(followerId)))
          .willThrow(new FollowException(FollowErrorCode.FOLLOW_DUPLICATE));

      // when, then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-002"));
    }

    @Test
    @DisplayName("요청자를 찾을 수 없으면 401을 반환")
    void createFollowFailWhenRequesterNotFound() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followeeId);
      given(followService.follow(any(FollowRequest.class), eq(followerId)))
          .willThrow(new FollowException(FollowErrorCode.FOLLOW_FOLLOWER_NOT_FOUND));

      // when, then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-004"));
    }
  }

  @Nested
  @DisplayName("팔로우 취소 [DELETE /api/follows/{followId}]")
  class CancelFollow {

    @Test
    @DisplayName("팔로우 취소에 성공하면 204와 빈 본문을 반환")
    void cancelFollowSuccess() throws Exception {
      // when, then
      mockMvc.perform(delete("/api/follows/{followId}", followId)
              .with(user(principal))
              .with(csrf()))
          .andDo(print())
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));
      
      verify(followService).unFollow(followId, followerId);
    }

    @Test
    @DisplayName("팔로우를 찾을 수 없으면 400을 반환")
    void cancelFollowFailWhenFollowNotFound() throws Exception {
      // given
      willThrow(new FollowException(FollowErrorCode.UNFOLLOW_NOT_FOUND))
          .given(followService).unFollow(followId, followerId);

      // when, then
      mockMvc.perform(delete("/api/follows/{followId}", followId)
              .with(user(principal))
              .with(csrf()))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-005"));
    }

    @Test
    @DisplayName("본인의 팔로우가 아니면 403을 반환")
    void cancelFollowFailWhenNotOwner() throws Exception {
      // given
      willThrow(new FollowException(FollowErrorCode.UNFOLLOW_NOT_OWNER))
          .given(followService).unFollow(followId, followerId);

      // when, then
      mockMvc.perform(delete("/api/follows/{followId}", followId)
              .with(user(principal))
              .with(csrf()))
          .andDo(print())
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-006"));
    }

    @Test
    @DisplayName("followId가 UUID 형식이 아니면 400을 반환")
    void cancelFollowFailWhenFollowIdIsNotUuid() throws Exception {
      // when, then
      mockMvc.perform(delete("/api/follows/{followId}", "not-a-uuid")
              .with(user(principal))
              .with(csrf()))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("COMMON-001"));

      verifyNoInteractions(followService);
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환")
    void cancelFollowFailWhenUnauthenticated() throws Exception {
      // when, then
      mockMvc.perform(delete("/api/follows/{followId}", followId)
              .with(csrf())
              .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(followService);
    }
  }

  @Nested
  @DisplayName("팔로우 여부 조회 [GET /api/follows/followed-by-me]")
  class IsFollowedByMe {

    @Test
    @DisplayName("팔로우 중이면 200과 팔로우 정보를 반환")
    void isFollowedByMeSuccess() throws Exception {
      // given
      given(followService.getFollowByMe(followeeId, followerId))
          .willReturn(new FollowResponse(followId, followeeId, followerId));

      // when, then
      mockMvc.perform(get("/api/follows/followed-by-me")
              .param("followeeId", followeeId.toString())
              .with(user(principal)))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(followId.toString()))
          .andExpect(jsonPath("$.followeeId").value(followeeId.toString()))
          .andExpect(jsonPath("$.followerId").value(followerId.toString()));
      
      verify(followService).getFollowByMe(followeeId, followerId);
    }

    @Test
    @DisplayName("팔로우 중이 아니면 404를 반환")
    void isFollowedByMeFailWhenNotFollowing() throws Exception {
      // given
      given(followService.getFollowByMe(followeeId, followerId))
          .willThrow(new FollowException(FollowErrorCode.FOLLOW_BY_ME_NOT_FOUND));

      // when, then
      mockMvc.perform(get("/api/follows/followed-by-me")
              .param("followeeId", followeeId.toString())
              .with(user(principal)))
          .andDo(print())
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-007"));
    }

    @Test
    @DisplayName("followeeId가 UUID 형식이 아니면 400을 반환")
    void isFollowedByMeFailWhenFolloweeIdIsNotUuid() throws Exception {
      // when, then
      mockMvc.perform(get("/api/follows/followed-by-me")
              .param("followeeId", "not-a-uuid")
              .with(user(principal)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("COMMON-001"));

      verifyNoInteractions(followService);
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환")
    void isFollowedByMeFailWhenUnauthenticated() throws Exception {
      // when, then
      mockMvc.perform(get("/api/follows/followed-by-me")
              .param("followeeId", followeeId.toString())
              .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(followService);
    }
  }

  @Nested
  @DisplayName("팔로워 수 조회 [GET /api/follows/count]")
  class GetFollowerCount {

    @Test
    @DisplayName("조회에 성공하면 200과 숫자를 그대로 반환")
    void getFollowerCountSuccess() throws Exception {
      // given
      given(followService.getFollowCount(followeeId)).willReturn(7L);

      // when, then
      mockMvc.perform(get("/api/follows/count")
              .param("followeeId", followeeId.toString())
              .with(user(principal)))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(content().string("7"));

      verify(followService).getFollowCount(followeeId);
    }

    @Test
    @DisplayName("followeeId가 UUID 형식이 아니면 400을 반환")
    void getFollowerCountFailWhenFolloweeIdIsNotUuid() throws Exception {
      // when, then
      mockMvc.perform(get("/api/follows/count")
              .param("followeeId", "not-a-uuid")
              .with(user(principal)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("COMMON-001"));

      verifyNoInteractions(followService);
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환")
    void getFollowerCountFailWhenUnauthenticated() throws Exception {
      // when, then
      mockMvc.perform(get("/api/follows/count")
              .param("followeeId", followeeId.toString())
              .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(followService);
    }
  }
}