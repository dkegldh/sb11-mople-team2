package com.codeit.mople.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.user.entity.Role;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtilsTest {

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
  @DisplayName("인증된 CustomUserDetails가 있으면 그대로 반환함")
  void returnsPrincipal_whenAuthenticated() {
    UUID userId = UUID.randomUUID();
    setAuth(userId, Role.USER);

    CustomUserDetails principal = SecurityUtils.currentPrincipal();

    assertThat(principal.getUserId()).isEqualTo(userId);
  }

  @Test
  @DisplayName("Authentication이 없으면(null) 예외가 발생함")
  void throws_whenAuthenticationIsNull() {
    SecurityContextHolder.getContext().setAuthentication(null);

    assertThatThrownBy(SecurityUtils::currentPrincipal)
        .isInstanceOf(AuthException.class);
  }

  @Test
  @DisplayName("principal이 CustomUserDetails가 아니면(익명 사용자) 예외가 발생함")
  void throws_whenPrincipalIsNotCustomUserDetails() {
    SecurityContextHolder.getContext().setAuthentication(
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    assertThatThrownBy(SecurityUtils::currentPrincipal)
        .isInstanceOf(AuthException.class);
  }

  @Test
  @DisplayName("인증된 사용자의 id를 반환함")
  void returnsAuthenticatedUserId() {
    UUID userId = UUID.randomUUID();
    setAuth(userId, Role.USER);

    assertThat(SecurityUtils.currentUserId()).isEqualTo(userId);
  }

  @Test
  @DisplayName("ADMIN이면 true를 반환함")
  void returnsTrue_whenAdmin() {
    setAuth(UUID.randomUUID(), Role.ADMIN);

    assertThat(SecurityUtils.isAdmin()).isTrue();
  }

  @Test
  @DisplayName("USER면 false를 반환함")
  void returnsFalse_whenUser() {
    setAuth(UUID.randomUUID(), Role.USER);

    assertThat(SecurityUtils.isAdmin()).isFalse();
  }
}
