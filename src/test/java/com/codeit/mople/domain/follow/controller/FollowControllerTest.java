package com.codeit.mople.domain.follow.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.dto.FollowResponse;
import com.codeit.mople.domain.follow.exception.FollowErrorCode;
import com.codeit.mople.domain.follow.exception.FollowException;
import com.codeit.mople.domain.follow.service.FollowService;
import com.codeit.mople.domain.user.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FollowController.class)
class FollowControllerTest {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @MockitoBean
  FollowService followService;

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
    @DisplayName("201과 생성된 팔로우를 반환하고, 인증 주체의 id를 followerId로 전달")
    void 성공_시_201_반환() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followeeId);
      given(followService.follow(any(FollowRequest.class), eq(followerId)))
          .willReturn(new FollowResponse(followId, followeeId, followerId));

      // when & then
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

      // followerId는 인증주체에서 옴
      verify(followService).follow(request, followerId);
    }

    @Test
    @DisplayName("followeeId가 없으면 400을 반환")
    void followeeId_누락_시_400을_반환() throws Exception {
      // given
      String request = "{}";

      // when & then
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
    @DisplayName("자기 자신을 팔로우하면 400을 반환")
    void 자기_자신_팔로우_시_400_반환() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followerId);
      given(followService.follow(any(FollowRequest.class), eq(followerId)))
          .willThrow(new FollowException(FollowErrorCode.FOLLOW_SELF_NOT_ALLOWED));

      // when & then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("FOLLOW-001"));
    }

    @Test
    @DisplayName("팔로우 대상이 존재하지 않으면 400를 반환한다")
    void 팔로우_대상이_존재하지_않으면_400를_반환() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followeeId);
      given(followService.follow(any(FollowRequest.class), eq(followerId)))
          .willThrow(new FollowException(FollowErrorCode.FOLLOW_FOLLOWEE_NOT_FOUND));

      // when & then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("FOLLOW-003"));
    }

    @Test
    @DisplayName("이미 팔로우 중이면 400를 반환")
    void 중복_팔로우_시_400_반환() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followeeId);
      given(followService.follow(any(FollowRequest.class), eq(followerId)))
          .willThrow(new FollowException(FollowErrorCode.FOLLOW_DUPLICATE));

      // when & then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("FOLLOW-002"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환")
      // TODO 주혁: SecurityConfig 작성되면 응답 검증 및 토큰 만료/변조 케이스 추가 예정
    void 미인증_요청_시_401_반환() throws Exception {
      // given
      FollowRequest request = new FollowRequest(followeeId);

      // when & then
      mockMvc.perform(post("/api/follows")
              .with(csrf())
              // SecurityConfig 검증 위치
              .accept(MediaType.APPLICATION_JSON)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(followService);
    }
  }
}
