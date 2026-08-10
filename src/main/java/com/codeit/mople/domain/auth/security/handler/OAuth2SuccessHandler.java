package com.codeit.mople.domain.auth.security.handler;

import com.codeit.mople.domain.auth.security.CustomOAuth2User;
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

  private final JwtProvider jwtProvider;
  private final UserRepository userRepository;

  @Value("${oauth2.redirect.success-uri}")
  private String successUri;

  @Value("${cookie.secure:true}")
  private boolean cookieSecure;

  @Transactional
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException {
    CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
    User user = userRepository.findById(principal.getUserId())
        .orElseThrow(() -> new IllegalStateException("OAuth 로그인 후 사용자를 찾을 수 없습니다."));

    long sessionVersion = user.increaseSessionVersion();
    String refreshToken = jwtProvider.createRefreshToken(user.getId());
    Instant refreshExpiresAt = Instant.now().plusMillis(jwtProvider.getRefreshTokenExpiration());
    user.updateRefreshToken(refreshToken, refreshExpiresAt);

    ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/api/auth")
        .maxAge(Duration.between(Instant.now(), refreshExpiresAt).getSeconds())
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    getRedirectStrategy().sendRedirect(request, response, successUri);
  }
}
