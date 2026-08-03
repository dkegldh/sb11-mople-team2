package com.codeit.mople.domain.auth.controller;

import com.codeit.mople.domain.auth.dto.request.SignInRequest;
import com.codeit.mople.domain.auth.dto.response.TokenResponse;
import com.codeit.mople.domain.auth.service.AuthService;
import com.codeit.mople.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/sign-in")
  public ApiResponse<TokenResponse> signIn(@Valid @ModelAttribute SignInRequest request) {
    return ApiResponse.success(authService.signIn(request));
  }

  @PostMapping("/sign-out")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void signOut() {
    // 서버 측 별도 처리 없음. 클라이언트가 토큰은 폐기하는 것으로 로그아웃 완료.
    // (sessionVersion은 재로그인 시 강제 로그아웃을 위해 sign-in 시점에만 증가)
  }

  @GetMapping("/csrf-token")
  public ApiResponse<Void> csrfToken(CsrfToken csrfToken) {
    csrfToken.getToken();
    return ApiResponse.success();
  }
}
