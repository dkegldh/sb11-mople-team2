package com.codeit.mople.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mople.domain.user.dto.request.UserCreateRequest;
import com.codeit.mople.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class UserControllerTest {

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

  @Autowired
  private SessionTokenRepository sessionTokenRepository;

  private final List<UUID> issuedSessionUserIds = new ArrayList<>();

  @AfterEach
  void tearDown() {
    userRepository.deleteAll();
    issuedSessionUserIds.forEach(sessionTokenRepository::invalidate);
    issuedSessionUserIds.clear();
  }

  private String tokenFor(User user) {
    String jti = UUID.randomUUID().toString();
    String token = jwtProvider.createAccessToken(user.getId(), jti, user.getRole());
    sessionTokenRepository.save(user.getId(), jti, Duration.ofDays(7));
    issuedSessionUserIds.add(user.getId());
    return token;
  }

  private MockMultipartFile requestPart(String name) throws Exception {
    return new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsBytes(new UserUpdateRequest(name)));
  }

  @Test
  @DisplayName("회원가입 성공")
  void signUp_success() throws Exception {
    UserCreateRequest request = new UserCreateRequest("test@test.com", "rawPw123", "testUser");

    mockMvc.perform(post("/api/users")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("test@test.com"));

    User savedUser = userRepository.findByEmail("test@test.com")
        .orElseThrow(() -> new AssertionError("가입된 사용자를 찾을 수 없습니다."));

    assertThat(savedUser.getPassword()).isNotEqualTo("rawPw123");
    assertThat(passwordEncoder.matches("rawPw123", savedUser.getPassword())).isTrue();
  }

  @Test
  @DisplayName("이메일 형식이 유효하지 않으면 400을 반환")
  void signUp_returnsBadRequest_whenEmailInvalid() throws Exception {
    UserCreateRequest request = new UserCreateRequest("invalid-email", "rawPw123", "testUser");

    mockMvc.perform(post("/api/users")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("이메일이 중복되면 409를 반환하고 중복된 이메일 정보를 포함")
  void signUp_returnsConflict_whenEmailDuplicated() throws Exception {
    userRepository.save(User.createUser("dup@test.com", "encoded", "oldUser"));

    UserCreateRequest request = new UserCreateRequest("dup@test.com", "rawPw123", "newUser");

    mockMvc.perform(post("/api/users")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("USER-002"))
        .andExpect(jsonPath("$.error.details.email").value("dup@test.com"));
  }

  @Test
  @DisplayName("사용자 상세 조회 성공")
  void getUser_success() throws Exception {
    User user = userRepository.save(User.createUser("get@test.com", "encoded", "getUser"));
    String token = tokenFor(user);

    mockMvc.perform(get("/api/users/{userId}", user.getId())
            .header("Authorization", "Bearer " + token))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("get@test.com"));
  }

  @Test
  @DisplayName("존재하지 않는 사용자를 조회하면 404를 반환")
  void getUser_returnsNotFound_whenUserNotExists() throws Exception {
    User requester = userRepository.save(User.createUser("requester@test.com", "encoded", "requester"));
    String token = tokenFor(requester);

    mockMvc.perform(get("/api/users/{userId}", UUID.randomUUID())
            .header("Authorization", "Bearer " + token))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("USER-001"));
  }

  @Test
  @DisplayName("어드민만 사용자 목록을 조회할 수 있음")
  void getUsers_success_whenAdmin() throws Exception {
    User admin = userRepository.save(User.createUser("admin@test.com", "encoded", "admin"));
    admin.changeRole(Role.ADMIN);
    userRepository.save(admin);
    String adminToken = tokenFor(admin);

    userRepository.save(User.createUser("a@test.com", "encoded", "aa"));
    userRepository.save(User.createUser("b@test.com", "encoded", "bb"));

    mockMvc.perform(get("/api/users")
        .param("limit", "10")
        .param("sortBy", "NAME")
        .param("sortDirection", "ASCENDING")
        .header("Authorization", "Bearer " + adminToken))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  @DisplayName("어드민이 아닌 사용자는 사용자 목록 조회 시 403을 반환함")
  void getUsers_returnsForbidden_whenNotAdmin() throws Exception {
    User normalUser = userRepository.save(User.createUser("nomal@test.com", "encoded", "normalUser"));
    String normalUserToken = tokenFor(normalUser);

    mockMvc.perform(get("/api/users")
        .param("limit", "10")
        .header("Authorization", "Bearer " + normalUserToken))
        .andDo(print())
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("limit이 100을 초과하면 400을 반환함")
  void getUsers_returnsBadRequest_whenLimitExceedsMax() throws Exception {
    User admin = userRepository.save(User.createUser("admin2@test.com", "encoded", "admin"));
    admin.changeRole(Role.ADMIN);
    userRepository.save(admin);
    String adminToken = tokenFor(admin);

    mockMvc.perform(get("/api/users")
        .param("limit", "500")
        .header("Authorization", "Bearer " + adminToken))
        .andDo(print())
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("limit이 0 이하면 400을 반환함")
  void getUsers_returnsBadRequest_whenLimitIsZeroOrNegative() throws Exception {
    User admin = userRepository.save(User.createUser("admin3@test.com", "encoded", "admin"));
    admin.changeRole(Role.ADMIN);
    userRepository.save(admin);
    String adminToken = tokenFor(admin);

    mockMvc.perform(get("/api/users")
        .param("limit", "0")
        .header("Authorization", "Bearer " + adminToken))
        .andDo(print())
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("이름만 전달하면 이름만 변경")
  void updateProfile_success_nameOnly() throws Exception {
    User user = userRepository.save(User.createUser("update@test.com", "encoded", "oldName"));
    String token = tokenFor(user);

    mockMvc.perform(multipart("/api/users/{userId}", user.getId())
        .file(requestPart("newName"))
        .header("Authorization", "Bearer " + token)
        .with(req -> { req.setMethod("PATCH"); return req; })
        .with(csrf()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("newName"));
  }

  @Test
  @DisplayName("이름과 프로필 이미지를 함께 전달하면 둘 다 변경")
  void updateProfile_success_withImage() throws Exception {
    User user = userRepository.save(User.createUser("update2@test.com", "encoded", "oldName"));
    String token = tokenFor(user);
    MockMultipartFile image = new MockMultipartFile("image", "test.jpg", "image/jpeg", "content".getBytes());

    mockMvc.perform(multipart("/api/users/{userId}", user.getId())
        .file(requestPart("newName"))
        .file(image)
        .header("Authorization", "Bearer " + token)
        .with(req -> { req.setMethod("PATCH"); return req; })
        .with(csrf()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("newName"))
        .andExpect(jsonPath("$.profileImageUrl").isNotEmpty());
  }

  @Test
  @DisplayName("이름이 최대 길이를 초과하면 400을 반환")
  void updateProfile_returnsBadRequest_whenNameTooLong() throws Exception {
    User user = userRepository.save(User.createUser("longname@test.com", "encoded", "oldName"));
    String token = tokenFor(user);
    String tooLongName = "a".repeat(21);

    mockMvc.perform(multipart("/api/users/{userId}", user.getId())
        .file(requestPart(tooLongName))
        .header("Authorization", "Bearer " + token)
        .with(req -> { req.setMethod("PATCH"); return req; })
        .with(csrf()))
        .andDo(print())
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("본인이 아닌 사용자가 프로필을 수정하면 403을 반환")
  void updateProfile_returnsForbidden_whenNotOwner() throws Exception {
    User owner = userRepository.save(User.createUser("owner@test.com", "encoded", "owner"));
    User attacker = userRepository.save(User.createUser("attacker@test.com", "encoded", "attacker"));
    String attackerToken = tokenFor(attacker);

    mockMvc.perform(multipart("/api/users/{userId}", owner.getId())
        .file(requestPart("newName"))
        .header("Authorization", "Bearer " + attackerToken)
        .with(req -> { req.setMethod("PATCH"); return req; })
        .with(csrf()))
        .andDo(print())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("COMMON-003"));
  }

  @Test
  @DisplayName("비밀번호 변경 성공")
  void changePassword_success() throws Exception {
    User user = userRepository.save(User.createUser("pw@test.com", passwordEncoder.encode("oldPw123"), "testUser"));
    String token = tokenFor(user);
    ChangePasswordRequest request = new ChangePasswordRequest("newPw123");

    mockMvc.perform(patch("/api/users/{userId}/password", user.getId())
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request))
            .with(csrf()))
        .andDo(print())
        .andExpect(status().isNoContent());

    User updateUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(passwordEncoder.matches("newPw123", updateUser.getPassword())).isTrue();
  }

  @Test
  @DisplayName("본인이 아닌 사용자가 비밀번호를 변경하면 403을 반환")
  void changePassword_returnsForbidden_whenNotOwner() throws Exception {
    User owner = userRepository.save(User.createUser("pw2@test.com", "encoded", "testUser"));
    User attacker = userRepository.save(User.createUser("attacker2@test.com", "encoded", "attacker"));
    String attackerToken = tokenFor(attacker);
    ChangePasswordRequest request = new ChangePasswordRequest("newPw123");

    mockMvc.perform(patch("/api/users/{userId}/password", owner.getId())
            .header("Authorization", "Bearer " + attackerToken)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request))
            .with(csrf()))
        .andDo(print())
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CSRF 토큰 없이 프로필 수정 시 403을 반환한다")
  void updateProfile_returnsForbidden_whenNoCsrf() throws Exception {
    User user = userRepository.save(User.createUser("nocsrf@test.com", "encoded", "테스트"));
    String token = tokenFor(user);

    mockMvc.perform(multipart("/api/users/{userId}", user.getId())
            .file(requestPart("newName"))
            .header("Authorization", "Bearer " + token)
            .with(req -> { req.setMethod("PATCH"); return req; }))
        // .with(csrf()) 없음!
        .andDo(print())
        .andExpect(status().isForbidden());  // 403이 나오면 CSRF가 진짜 살아있는 것
  }
}
