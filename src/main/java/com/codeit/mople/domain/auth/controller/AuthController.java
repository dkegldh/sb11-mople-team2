package com.codeit.mople.domain.auth.controller;

import com.codeit.mople.domain.auth.controller.api.AuthApi;
import com.codeit.mople.domain.auth.dto.request.ResetPasswordRequest;
import com.codeit.mople.domain.auth.dto.request.SignInRequest;
import com.codeit.mople.domain.auth.dto.response.AuthTokens;
import com.codeit.mople.domain.auth.dto.response.TokenResponse;
import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.auth.service.AuthService;
import com.codeit.mople.global.error.CommonErrorCode;
import com.codeit.mople.global.error.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

  private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

  private final AuthService authService;

  @Value("${cookie.secure:true}")
  private boolean cookieSecure;

  @Value("${password-reset.enabled:false}")
  private boolean passwordResetEnabled;

  @Override
  @PostMapping(value = "/sign-in", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<TokenResponse> signIn(
      @Valid @ModelAttribute SignInRequest request, HttpServletRequest servletRequest) {
    if (servletRequest.getQueryString() != null) {
      // 쿼리 스트링으로 로그인 정보가 전달되면 접근/프록시 로그에 비밀번호가 남을 수 있어 차단한다.
      throw new CustomException(CommonErrorCode.INVALID_INPUT);
    }
    AuthTokens tokens = authService.signIn(request);
    return withRefreshTokenCookie(tokens);
  }

  @Override
  @PostMapping("/sign-out")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> signOut(
      @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
    authService.signOut(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, expireRefreshTokenCookie().toString())
        .build();
  }

  @Override
  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
    if(!passwordResetEnabled) {
      // 이메일 발송이 검증되기 전까지 비활성화. 엔드포인트 존재 자체를 숨기기 위해 404로 응답.
      throw new CustomException(CommonErrorCode.NOT_FOUND);
    }
    authService.resetPassword(request, resolveClientIp(servletRequest));
  }

  @Override
  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(
      @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
    if(refreshToken == null) {
      throw new AuthException(AuthErrorCode.INVALID_TOKEN);
    }
    AuthTokens tokens = authService.refresh(refreshToken);
    return withRefreshTokenCookie(tokens);
  }

  @Override
  @GetMapping("/csrf-token")
  public ResponseEntity<Void> csrfToken(CsrfToken csrfToken) {
    csrfToken.getToken();
    return ResponseEntity.ok().build();
  }

  private ResponseEntity<TokenResponse> withRefreshTokenCookie(AuthTokens tokens) {
    ResponseCookie cookie = buildRefreshTokenCookie(tokens.refreshToken(), tokens.refreshTokenExpiresAt());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(new TokenResponse(tokens.accessToken(), tokens.userDto()));
  }

  private ResponseCookie buildRefreshTokenCookie(String refreshToken, Instant expiresAt) {
    long maxAgeSeconds = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/api/auth")
        .maxAge(maxAgeSeconds)
        .build();
  }

  private ResponseCookie expireRefreshTokenCookie() {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/api/auth")
        .maxAge(0)
        .build();
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if(forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
