package com.codeit.mople.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("username", "test@test.com")
        .param("password", "rawPw123"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  @Test
  @DisplayName("로그인 정보를 쿼리 스트링으로 전달하면 400을 반환")
  void signIn_returnsBadRequest_whenCredentialsInQueryString() throws Exception {
    userRepository.save(User.createUser("query@test.com", passwordEncoder.encode("rawPw123"), "testUser"));

    mockMvc.perform(post("/api/auth/sign-in?username=query@test.com&password=rawPw123")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andDo(print())
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("로그인 성공 시 Refresh Token 쿠키가 HttpOnly, Path=/api/auth, 양수 Max-Age로 내려감")
  void signIn_success_setsRefreshTokenCookieAttributes() throws Exception {
    userRepository.save(User.createUser("cookie@test.com", passwordEncoder.encode("rawPw123"), "testUser"));

    MvcResult result = mockMvc.perform(post("/api/auth/sign-in")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", "cookie@test.com")
            .param("password", "rawPw123"))
        .andExpect(status().isOk())
        .andReturn();

    Cookie refreshTokenCookie = result.getResponse().getCookie("refreshToken");
    assertThat(refreshTokenCookie).isNotNull();
    assertThat(refreshTokenCookie.isHttpOnly()).isTrue();
    assertThat(refreshTokenCookie.getPath()).isEqualTo("/api/auth");
    assertThat(refreshTokenCookie.getMaxAge()).isGreaterThan(0);
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 로그인하면 401을 반환")
  void signIn_returnsUnauthorized_whenEmailNotFound() throws Exception {
    mockMvc.perform(post("/api/auth/sign-in")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
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
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
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
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
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
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("username", "multi@test.com")
        .param("password", "rawPw123"))
        .andReturn().getResponse().getContentAsString();

    String oldToken = objectMapper.readTree(firstResponse).get("accessToken").asText();

    // 재로그인 전, 첫 토큰이 실제로 유효한지 먼저 확인
    mockMvc.perform(get("/api/users/{userId}", user.getId())
                    .header("Authorization", "Bearer " + oldToken))
            .andDo(print())
            .andExpect(status().isOk());

    String secondResponse = mockMvc.perform(post("/api/auth/sign-in")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("username", "multi@test.com")
                    .param("password", "rawPw123"))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    String newToken = objectMapper.readTree(secondResponse).get("accessToken").asText();

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
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("username", "locked@test.com")
        .param("password", "rawPw123"))
        .andDo(print())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH-004"));
  }

  @Test
  @DisplayName("로그인 상태에서 계정이 잠기면 기존 토큰은 403(LOCKED_ACCOUNT)으로 거부됨")
  void existingToken_becomesLocked_whenAccountGetsLockedAfterward() throws Exception {
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

    // 같은 토큰으로 재요청 -> 신원은 확인됐으나 접근이 막힌 상태이므로 401이 아닌 403
    mockMvc.perform(get("/api/users/{userId}", user.getId())
        .header("Authorization", "Bearer " + token))
        .andDo(print())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH-004"));
  }

  @Test
  @DisplayName("로그아웃 요청은 204를 반환")
  void signOut_success() throws Exception {
    User user = userRepository.save(User.createUser("signout@test.com", passwordEncoder.encode("rawPw123"), "testUser"));
    String accessToken = jwtProvider.createAccessToken(user.getId(), user.getSessionVersion());

    mockMvc.perform(post("/api/auth/sign-out")
            .header("Authorization", "Bearer " + accessToken))
        .andDo(print())
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("인증 없이 로그아웃을 요청하면 401을 반환")
  void signOut_returnsUnauthorized_whenNotAuthenticated() throws Exception {
    mockMvc.perform(post("/api/auth/sign-out"))
        .andDo(print())
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("로그아웃 시 서버 측 인증 상태가 폐기되어 기존 Access/Refresh Token이 모두 무효화됨")
  void signOut_invalidatesExistingTokens() throws Exception {
    User user = userRepository.save(User.createUser("logout@test.com", passwordEncoder.encode("rawPw123"), "testUser"));

    MvcResult signInResult = mockMvc.perform(post("/api/auth/sign-in")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", "logout@test.com")
            .param("password", "rawPw123"))
        .andExpect(status().isOk())
        .andReturn();

    String accessToken = objectMapper.readTree(signInResult.getResponse().getContentAsString())
        .get("accessToken").asText();
    Cookie refreshTokenCookie = signInResult.getResponse().getCookie("refreshToken");
    assertThat(refreshTokenCookie).isNotNull();

    mockMvc.perform(post("/api/auth/sign-out")
            .header("Authorization", "Bearer " + accessToken)
            .cookie(refreshTokenCookie))
        .andDo(print())
        .andExpect(status().isNoContent());

    // 기존 Access Token은 sessionVersion 불일치로 인증이 거부됨
    mockMvc.perform(get("/api/users/{userId}", user.getId())
            .header("Authorization", "Bearer " + accessToken))
        .andDo(print())
        .andExpect(status().isUnauthorized());

    // 기존 Refresh Token으로는 재발급도 거부됨
    mockMvc.perform(post("/api/auth/refresh")
            .cookie(refreshTokenCookie))
        .andDo(print())
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Refresh Token으로 재발급 요청 시 새 Access/Refresh Token이 발급되고 이전 Refresh Token은 Rotation으로 무효화됨")
  void refresh_success_rotatesTokens() throws Exception {
    userRepository.save(User.createUser("rotate@test.com", passwordEncoder.encode("rawPw123"), "testUser"));

    MvcResult signInResult = mockMvc.perform(post("/api/auth/sign-in")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", "rotate@test.com")
            .param("password", "rawPw123"))
        .andExpect(status().isOk())
        .andReturn();

    Cookie oldRefreshTokenCookie = signInResult.getResponse().getCookie("refreshToken");
    assertThat(oldRefreshTokenCookie).isNotNull();

    MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
            .cookie(oldRefreshTokenCookie))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andReturn();

    Cookie newRefreshTokenCookie = refreshResult.getResponse().getCookie("refreshToken");
    assertThat(newRefreshTokenCookie).isNotNull();
    assertThat(newRefreshTokenCookie.getValue()).isNotEqualTo(oldRefreshTokenCookie.getValue());

    // 이전 Refresh Token은 Rotation으로 인해 더 이상 사용할 수 없음
    mockMvc.perform(post("/api/auth/refresh")
            .cookie(oldRefreshTokenCookie))
        .andDo(print())
        .andExpect(status().isUnauthorized());

    // 새로 발급된 Refresh Token은 정상적으로 재발급에 사용할 수 있음
    mockMvc.perform(post("/api/auth/refresh")
            .cookie(newRefreshTokenCookie))
        .andDo(print())
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Refresh Token 쿠키가 없으면 재발급 요청이 401을 반환")
  void refresh_returnsUnauthorized_whenCookieMissing() throws Exception {
    mockMvc.perform(post("/api/auth/refresh"))
        .andDo(print())
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("유효하지 않은 Refresh Token 쿠키면 재발급 요청이 401을 반환")
  void refresh_returnsUnauthorized_whenCookieInvalid() throws Exception {
    mockMvc.perform(post("/api/auth/refresh")
            .cookie(new Cookie("refreshToken", "not-a-valid-jwt")))
        .andDo(print())
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("로그아웃 응답의 Refresh Token 쿠키는 즉시 만료되도록 Max-Age=0, 빈 값으로 내려감")
  void signOut_expiresRefreshTokenCookie() throws Exception {
    User user = userRepository.save(User.createUser("signoutcookie@test.com", passwordEncoder.encode("rawPw123"), "testUser"));
    String accessToken = jwtProvider.createAccessToken(user.getId(), user.getSessionVersion());

    MvcResult result = mockMvc.perform(post("/api/auth/sign-out")
            .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNoContent())
        .andReturn();

    Cookie refreshTokenCookie = result.getResponse().getCookie("refreshToken");
    assertThat(refreshTokenCookie).isNotNull();
    assertThat(refreshTokenCookie.getMaxAge()).isZero();
    assertThat(refreshTokenCookie.getValue()).isEmpty();
  }

  @Test
  @DisplayName("CSRF 토큰 발급 요청은 200을 반환하고 쿠키를 내려줌")
  void csrfToken_success() throws Exception {
    mockMvc.perform(get("/api/auth/csrf-token"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(cookie().exists("XSRF-TOKEN"));
  }

  @Test
  @DisplayName("발급받은 CSRF 쿠키 값을 헤더에 그대로 실어 보내면 상태 변경 요청이 통과함")
  void csrfCookie_allowsStateChangingRequest() throws Exception {
    User admin = userRepository.save(User.createAdmin("csrfAdmin@test.com", passwordEncoder.encode("rawPw123"), "csrfAdmin"));
    User target = userRepository.save(User.createUser("csrfTarget@test.com", passwordEncoder.encode("rawPw123"), "csrfTarget"));
    String adminToken = jwtProvider.createAccessToken(admin.getId(), admin.getSessionVersion());

    Cookie xsrf = mockMvc.perform(get("/api/auth/csrf-token"))
        .andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrf).isNotNull();

    mockMvc.perform(patch("/api/users/{userId}/locked", target.getId())
            .cookie(xsrf)
            .header("X-XSRF-TOKEN", xsrf.getValue())
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"locked\": true}"))
        .andDo(print())
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("CSRF 헤더 없이 상태 변경 요청을 보내면 403을 반환함")
  void missingCsrfHeader_rejectsStateChangingRequest() throws Exception {
    User admin = userRepository.save(User.createAdmin("csrfAdmin2@test.com", passwordEncoder.encode("rawPw123"), "csrfAdmin2"));
    User target = userRepository.save(User.createUser("csrfTarget2@test.com", passwordEncoder.encode("rawPw123"), "csrfTarget2"));
    String adminToken = jwtProvider.createAccessToken(admin.getId(), admin.getSessionVersion());

    Cookie xsrf = mockMvc.perform(get("/api/auth/csrf-token"))
        .andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrf).isNotNull();

    mockMvc.perform(patch("/api/users/{userId}/locked", target.getId())
            .cookie(xsrf)
            // 의도적으로 X-XSRF-TOKEN 헤더를 생략
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"locked\": true}"))
        .andDo(print())
        .andExpect(status().isForbidden());
  }
}
