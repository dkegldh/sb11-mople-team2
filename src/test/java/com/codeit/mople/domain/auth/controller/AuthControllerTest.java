package com.codeit.mople.domain.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private JwtProvider jwtProvider;

  @AfterEach
  void tearDown() {
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("로그인 성공 시 토큰을 발급")
  void signIn_success() throws Exception {
    userRepository.save(User.createUser("test@test.com", passwordEncoder.encode("rawPw123"), "testUser"));

    mockMvc.perform(post("/api/auth/sign-in")
        .param("username", "test@test.com")
        .param("password", "rawPw123"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 로그인하면 401을 반환")
  void signIn_returnsUnauthorized_whenEmailNotFound() throws Exception {
    mockMvc.perform(post("/api/auth/sign-in")
        .param("username", "nobody@test.com")
        .param("password", "rawPw123"))
        .andDo(print())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH-001"));
  }

  @Test
  @DisplayName("로그인 실패 응답에는 details가 포함되지 않는다")
  void signIn_returnsUnauthorized_withoutDetails() throws Exception {
    mockMvc.perform(post("/api/auth/sign-in")
            .param("username", "nobody@test.com")
            .param("password", "rawPw123"))
        .andDo(print())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH-001"))
        .andExpect(jsonPath("$.error.details").doesNotExist());
  }

  @Test
  @DisplayName("비밀번호가 틀리면 401을 반환")
  void signIn_returnsUnauthorized_whenPasswordWrong() throws Exception {
    userRepository.save(User.createUser("test2@test.com", passwordEncoder.encode("correctPw"), "testUser"));

    mockMvc.perform(post("/api/auth/sign-in")
        .param("username", "test2@test.com")
        .param("password", "wrongPw"))
        .andDo(print())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH-001"));
  }

  @Test
  @DisplayName("재로그인 시 이전 토큰으로는 인증이 필요한 API에 접근할 수 없음")
  void reSignIn_invalidatesOldToken() throws Exception {
    User user = userRepository.save(User.createUser("multi@test.com", passwordEncoder.encode("rawPw123"), "testUser"));

    String firstResponse = mockMvc.perform(post("/api/auth/sign-in")
        .param("username", "multi@test.com")
        .param("password", "rawPw123"))
        .andReturn().getResponse().getContentAsString();

    String oldToken = objectMapper.readTree(firstResponse).get("data").get("accessToken").asText();

    // 재로그인 전, 첫 토큰이 실제로 유효한지 먼저 확인
    mockMvc.perform(get("/api/users/{userId}", user.getId())
                    .header("Authorization", "Bearer " + oldToken))
            .andDo(print())
            .andExpect(status().isOk());

    String secondResponse = mockMvc.perform(post("/api/auth/sign-in")
                    .param("username", "multi@test.com")
                    .param("password", "rawPw123"))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    String newToken = objectMapper.readTree(secondResponse).get("data").get("accessToken").asText();

    // 이전 토큰은 무효화됨
    mockMvc.perform(get("/api/users/{userId}", user.getId())
        .header("Authorization", "Bearer " + oldToken))
        .andDo(print())
        .andExpect(status().isUnauthorized());

    // 새로 발급된 토큰은 정상적으로 인증됨
    mockMvc.perform(get("/api/users/{userId}", user.getId())
        .header("Authorization", "Bearer " + newToken))
        .andDo(print())
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("잠긴 계정으로 로그인하면 403을 반환")
  void signIn_returnsForbidden_whenAccountIsLocked() throws Exception {
    User user = userRepository.save(User.createUser("locked@test.com", passwordEncoder.encode("rawPw123"), "lockedUser"));
    user.lock();
    userRepository.save(user);

    mockMvc.perform(post("/api/auth/sign-in")
        .param("username", "locked@test.com")
        .param("password", "rawPw123"))
        .andDo(print())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH-004"));
  }

  @Test
  @DisplayName("로그인 상태에서 계정이 잠기면 기존 토큰도 인증이 거부됨")
  void existingToken_becomesInvalid_whenAccountGetsLockedAfterward() throws Exception {
    User user = userRepository.save(User.createUser("lockAfter@test.com", passwordEncoder.encode("rawPw123"), "tester"));
    String token = jwtProvider.createAccessToken(user.getId(), user.getSessionVersion());

    // 발급 직후엔 정상 인증됨을 먼저 확인
    mockMvc.perform(get("/api/users/{userId}", user.getId())
        .header("Authorization", "Bearer " + token))
        .andDo(print())
        .andExpect(status().isOk());

    // 이후 계정이 잠김
    user.lock();
    userRepository.save(user);

    // 같은 토큰으로 재요청 -> 401
    mockMvc.perform(get("/api/users/{userId}", user.getId())
        .header("Authorization", "Bearer " + token))
        .andDo(print())
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("로그아웃 요청은 204를 반환")
  void signOut_success() throws Exception {
    mockMvc.perform(post("/api/auth/sign-out"))
        .andDo(print())
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("CSRF 토큰 발급 요청은 200을 반환하고 쿠키를 내려줌")
  void csrfToken_success() throws Exception {
    mockMvc.perform(get("/api/auth/csrf-token"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(cookie().exists("XSRF-TOKEN"));
  }
}
