package com.codeit.mople.domain.auth.security.handler;

import com.codeit.mople.domain.auth.dto.response.RefreshToken;
import com.codeit.mople.domain.auth.security.CustomOAuth2User;
import com.codeit.mople.domain.auth.service.AuthService;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final AuthService authService;

  @Value("${oauth2.redirect.success-uri}")
  private String successUri;

  @Value("${cookie.secure:true}")
  private boolean cookieSecure;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException {
    CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();

    RefreshToken issuance = authService.issueOAuthRefreshToken(principal.getUserId());

    ResponseCookie cookie = ResponseCookie.from("refreshToken", issuance.refreshToken())
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/api/auth")
        .maxAge(Duration.between(Instant.now(), issuance.refreshTokenExpiredAt()).getSeconds())
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    getRedirectStrategy().sendRedirect(request, response, successUri);
  }
}
