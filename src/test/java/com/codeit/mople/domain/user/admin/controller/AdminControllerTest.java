package com.codeit.mople.domain.user.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.user.admin.dto.LockUpdateRequest;
import com.codeit.mople.domain.user.admin.dto.RoleUpdateRequest;
import com.codeit.mople.domain.user.admin.service.AdminService;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.SecurityConfig;
import com.codeit.mople.global.error.CustomException;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @MockitoBean
  AdminService adminService;

  @MockitoBean
  UserRepository userRepository;

  @MockitoBean
  JwtProvider jwtProvider;

  UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("사용자 권한 수정 [PATCH /api/users/{userId}/role]")
  class ChangeUserRole {

    @Test
    @DisplayName("ADMIN 권한으로 정상 요청 시 200과 성공 응답을 반환한다")
    void ADMIN_권한으로_정상_요청_시_200을_반환한다() throws Exception {
      // given
      RoleUpdateRequest request = new RoleUpdateRequest("USER");

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", userId)
              .with(user("admin").roles("ADMIN"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true));

      verify(adminService).changeUserRole(userId, "USER");
    }

    @Test
    @DisplayName("role 값이 빈 문자열이면 400을 반환하고 서비스를 호출하지 않는다")
    void role_값이_빈_문자열이면_400을_반환한다() throws Exception {
      // given
      String request = "{\"role\": \"\"}";

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", userId)
              .with(user("admin").roles("ADMIN"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(request))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("COMMON-001"));

      verifyNoInteractions(adminService);
    }

    @Test
    @DisplayName("존재하지 않는 userId면 404를 반환한다")
    void 존재하지_않는_userId면_404를_반환한다() throws Exception {
      // given
      RoleUpdateRequest request = new RoleUpdateRequest("ADMIN");
      willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND))
          .given(adminService).changeUserRole(any(UUID.class), anyString());

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", userId)
              .with(user("admin").roles("ADMIN"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("USER-001"));
    }

    @Test
    @DisplayName("허용되지 않은 role 값이면 400을 반환하고 서비스를 호출하지 않는다")
    void 허용되지_않은_role_값이면_400을_반환한다() throws Exception {
      // given
      String request = "{\"role\": \"INVALID_ROLE\"}";

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", userId)
              .with(user("admin").roles("ADMIN"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(request))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("COMMON-001"));

      verifyNoInteractions(adminService);
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
      // given
      RoleUpdateRequest request = new RoleUpdateRequest("ADMIN");

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", userId)
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(adminService);
    }

    @Test
    @DisplayName("ADMIN이 아닌 USER 권한으로 요청하면 403을 반환한다")
    void USER_권한으로_요청하면_403을_반환한다() throws Exception {
      // given
      RoleUpdateRequest request = new RoleUpdateRequest("ADMIN");

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", userId)
              .with(user("user").roles("USER"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isForbidden());

      verifyNoInteractions(adminService);
    }

    @Test
    @DisplayName("자신의 계정에 요청하면 400을 반환한다")
    void 자신의_계정에_요청하면_400을_반환한다() throws Exception {
      // given
      RoleUpdateRequest request = new RoleUpdateRequest("USER");
      willThrow(new CustomException(UserErrorCode.CANNOT_MODIFY_SELF))
          .given(adminService).changeUserRole(any(UUID.class), anyString());

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/role", userId)
              .with(user("admin").roles("ADMIN"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("USER-007"));
    }
  }

  @Nested
  @DisplayName("계정 잠금 상태 변경 [PATCH /api/users/{userId}/locked]")
  class ChangeLocked {

    @Test
    @DisplayName("ADMIN 권한으로 정상 요청 시 200과 성공 응답을 반환한다")
    void ADMIN_권한으로_정상_요청_시_200을_반환한다() throws Exception {
      // given
      LockUpdateRequest request = new LockUpdateRequest(true);

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/locked", userId)
              .with(user("admin").roles("ADMIN"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true));

      verify(adminService).changeUserLocked(userId, true);
    }

    @Test
    @DisplayName("locked 값이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void locked_값이_없으면_400을_반환한다() throws Exception {
      // given
      String request = "{}";

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/locked", userId)
              .with(user("admin").roles("ADMIN"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(request))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("COMMON-001"));

      verifyNoInteractions(adminService);
    }

    @Test
    @DisplayName("존재하지 않는 userId면 404를 반환한다")
    void 존재하지_않는_userId면_404를_반환한다() throws Exception {
      // given
      LockUpdateRequest request = new LockUpdateRequest(true);
      willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND))
          .given(adminService).changeUserLocked(any(UUID.class), anyBoolean());

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/locked", userId)
              .with(user("admin").roles("ADMIN"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("USER-001"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
      // given
      LockUpdateRequest request = new LockUpdateRequest(true);

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/locked", userId)
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isUnauthorized());

      verifyNoInteractions(adminService);
    }

    @Test
    @DisplayName("ADMIN이 아닌 USER 권한으로 요청하면 403을 반환한다")
    void USER_권한으로_요청하면_403을_반환한다() throws Exception {
      // given
      LockUpdateRequest request = new LockUpdateRequest(true);

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/locked", userId)
              .with(user("user").roles("USER"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isForbidden());

      verifyNoInteractions(adminService);
    }

    @Test
    @DisplayName("자신의 계정에 요청하면 400을 반환한다")
    void 자신의_계정에_요청하면_400을_반환한다() throws Exception {
      // given
      LockUpdateRequest request = new LockUpdateRequest(true);
      willThrow(new CustomException(UserErrorCode.CANNOT_MODIFY_SELF))
          .given(adminService).changeUserLocked(any(UUID.class), anyBoolean());

      // when & then
      mockMvc.perform(patch("/api/users/{userId}/locked", userId)
              .with(user("admin").roles("ADMIN"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("USER-007"));
    }
  }
}
