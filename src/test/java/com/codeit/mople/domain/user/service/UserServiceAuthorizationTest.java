package com.codeit.mople.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.codeit.mople.domain.auth.repository.RefreshTokenRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mople.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mople.domain.user.dto.response.UserDto;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.init.AdminInitializer;
import com.codeit.mople.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
public class UserServiceAuthorizationTest {

  @Autowired
  private UserService userService;

  @MockitoBean
  private UserRepository userRepository;

  @MockitoBean
  private PasswordEncoder passwordEncoder;

  @MockitoBean
  private SessionTokenRepository sessionTokenRepository;

  @MockitoBean
  private RefreshTokenRepository refreshTokenRepository;

  @MockitoBean
  private AdminInitializer adminInitializer;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private void setAuth(UUID userId, Role role) {
    CustomUserDetails principal = new CustomUserDetails(userId, role);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }

  @Test
  @DisplayName("본인도 ADMIN도 아니면 프로필을 수정할 수 없음")
  void updateProfile_deniedByPreAuthorize_whenNotOwnerAndNotAdmin() {
    UUID targetId = UUID.randomUUID();
    setAuth(UUID.randomUUID(), Role.USER);

    assertThatThrownBy(() ->
        userService.updateProfile(targetId, null, null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("ADMIN은 본인이 아니어도 프로필을 수정할 수 있음")
  void updateProfile_allowedByPreAuthorize_whenAdmin() {
    UUID targetId = UUID.randomUUID();
    User user = User.createUser("admin-target@test.com", "encoded", "originalName");
    when(userRepository.findById(targetId)).thenReturn(Optional.of(user));

    setAuth(UUID.randomUUID(), Role.ADMIN);
    UserUpdateRequest request = new UserUpdateRequest("newName");

    UserDto response = userService.updateProfile(targetId, request, null);

    assertThat(response.name()).isEqualTo("newName");
  }

  @Test
  @DisplayName("본인도 ADMIN도 아니면 비밀번호를 변경할 수 없음")
  void changePassword_deniedByPreAuthorize_whenNotOwnerAndNotAdmin() {
    UUID targetId = UUID.randomUUID();
    setAuth(UUID.randomUUID(), Role.USER);

    ChangePasswordRequest request = new ChangePasswordRequest("newPw123");

    assertThatThrownBy(() ->
        userService.changePassword(targetId, request))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("ADMIN은 본인이 아니어도 비밀번호를 변경할 수 있음")
  void changePassword_allowedByPreAuthorize_whenAdmin() {
    UUID targetId = UUID.randomUUID();
    User user = User.createUser("admin-target@test.com", "encoded", "name");
    when(userRepository.findById(targetId)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("newPw123")).thenReturn("encodedNewPw");

    setAuth(UUID.randomUUID(), Role.ADMIN);
    ChangePasswordRequest request = new ChangePasswordRequest("newPw123");

    userService.changePassword(targetId, request);

    assertThat(user.getPassword()).isEqualTo("encodedNewPw");
  }
}
