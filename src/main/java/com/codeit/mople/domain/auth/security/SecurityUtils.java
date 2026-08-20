package com.codeit.mople.domain.auth.security;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.user.entity.Role;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

  private SecurityUtils() {
  }

  public static CustomUserDetails currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if(auth == null || !(auth.getPrincipal() instanceof CustomUserDetails principal)) {
      throw new AuthException(AuthErrorCode.INVALID_TOKEN);
    }
    return principal;
  }

  public static UUID currentUserId() {
    return currentPrincipal().getUserId();
  }

  public static boolean isAdmin() {
    return currentPrincipal().getRole() == Role.ADMIN;
  }
}
