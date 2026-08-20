package com.codeit.mople.domain.follow.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.dto.FollowResponse;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@Import(SecurityConfig.class)
@Transactional
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("팔로우 통합 테스트")
class FollowIntegrationTest {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  UserRepository userRepository;

  User followee;
  User follower;
  User other;

  CustomUserDetails principal;

  @BeforeEach
  void setUp() {
    followee = userRepository.save(User.createUser("followee@mople.com", "password", "팔로우대상"));
    follower = userRepository.save(User.createUser("follower@mople.com", "password", "팔로워"));
    other = userRepository.save(User.createUser("other@mople.com", "password", "제3자"));

    principal = new CustomUserDetails(follower.getId(), Role.USER);
  }

  private FollowResponse createFollow(CustomUserDetails requester, User target) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/follows")
            .with(user(requester))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new FollowRequest(target.getId()))))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), FollowResponse.class);
  }

  @Nested
  @DisplayName("팔로우 성공")
  class FollowSuccess {

    @Test
    @DisplayName("팔로우한 뒤 취소하면 팔로우 여부 조회가 200에서 404로 바뀌는지")
    void followThenUnfollowRoundTrip() throws Exception {
      // given 팔로우 생성
      FollowResponse created = createFollow(principal, followee);

      // then 팔로우 중
      mockMvc.perform(get("/api/follows/followed-by-me")
              .param("followeeId", followee.getId().toString())
              .with(user(principal)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(created.id().toString()));

      // when 취소
      mockMvc.perform(delete("/api/follows/{followId}", created.id())
              .with(user(principal))
              .with(csrf()))
          .andExpect(status().isNoContent());

      // then 더 이상 팔로우 중이 아님
      mockMvc.perform(get("/api/follows/followed-by-me")
              .param("followeeId", followee.getId().toString())
              .with(user(principal)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-007"));
    }

    @Test
    @DisplayName("팔로우하면 팔로워 수가 count되는지")
    void followerCountReflectsFollow() throws Exception {
      // given
      createFollow(principal, followee);

      // when, then
      // 팔로워 수는 캐시되므로 이 테스트 안에서 한 번만 조회
      mockMvc.perform(get("/api/follows/count")
              .param("followeeId", followee.getId().toString())
              .with(user(principal)))
          .andExpect(status().isOk())
          .andExpect(content().string("1"));
    }
  }

  @Nested
  @DisplayName("팔로우 생성 실패")
  class CreateRejected {

    @Test
    @DisplayName("같은 대상을 두 번 팔로우하면 400을 반환")
    void duplicateFollowIsRejected() throws Exception {
      // given
      createFollow(principal, followee);

      // when, then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(new FollowRequest(followee.getId()))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-002"));
    }

    @Test
    @DisplayName("DB에 없는 대상을 팔로우하면 400을 반환")
    void followUnknownFolloweeIsRejected() throws Exception {
      // when, then
      mockMvc.perform(post("/api/follows")
              .with(user(principal))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(new FollowRequest(UUID.randomUUID()))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-003"));
    }

    @Test
    @DisplayName("DB에 없는 요청자로 팔로우하면 401을 반환")
    void followWithUnknownRequesterIsRejected() throws Exception {
      // given 토큰은 유효하지만 그 주체가 DB 에 없는 상황
      CustomUserDetails ghost = new CustomUserDetails(UUID.randomUUID(), Role.USER);

      // when, then
      mockMvc.perform(post("/api/follows")
              .with(user(ghost))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(new FollowRequest(followee.getId()))))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-004"));
    }
  }

  @Nested
  @DisplayName("팔로우 취소 실패")
  class CancelRejected {

    @Test
    @DisplayName("남의 팔로우를 취소하면 403을 반환")
    void cancelOthersFollowIsRejected() throws Exception {
      // given follower 가 생성한 팔로우를 other 가 취소
      FollowResponse created = createFollow(principal, followee);
      CustomUserDetails otherPrincipal = new CustomUserDetails(other.getId(), Role.USER);

      // when, then
      mockMvc.perform(delete("/api/follows/{followId}", created.id())
              .with(user(otherPrincipal))
              .with(csrf()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-006"));
    }

    @Test
    @DisplayName("없는 팔로우를 취소하면 400을 반환한다")
    void cancelUnknownFollowIsRejected() throws Exception {
      // when, then
      mockMvc.perform(delete("/api/follows/{followId}", UUID.randomUUID())
              .with(user(principal))
              .with(csrf()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("FOLLOW-005"));
    }
  }
}