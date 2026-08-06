package com.codeit.mople.domain.auth.controller.api;

import com.codeit.mople.domain.auth.dto.request.ResetPasswordRequest;
import com.codeit.mople.domain.auth.dto.request.SignInRequest;
import com.codeit.mople.domain.auth.dto.response.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
    name = "인증 관리",
    description = "로그인, 로그아웃, 비밀번호 재설정, 토큰 재발급 등 인증 관련 API"
)
public interface AuthApi {

  @Operation(
      summary = "로그인",
      description = "이메일과 비밀번호로 로그인합니다. Access Token은 응답 본문으로, "
          + "Refresh Token은 httpOnly 쿠키(refreshToken)로 발급됩니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = TokenResponse.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "이메일 또는 비밀번호가 올바르지 않음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "403",
          description = "잠긴 계정",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<TokenResponse> signIn(
      @Valid @ModelAttribute SignInRequest request,
      @Parameter(hidden = true) HttpServletRequest servletRequest
  );

  @Operation(
      summary = "로그아웃",
      description = "저장된 Refresh Token을 삭제하고 세션 버전을 증가시켜, "
          + "기존에 발급된 모든 Access/Refresh Token을 무효화합니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "204",
          description = "성공"
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<Void> signOut(
      @Parameter(hidden = true)
      @CookieValue(value = "refreshToken", required = false) String refreshToken
  );

  @Operation(
      summary = "비밀번호 초기화",
      description = "이메일 주소로 임시 비밀번호를 발급합니다. 임시 비밀번호는 발급 후 3분간 유효하며, "
          + "로그인 API를 그대로 활용해 인증할 수 있습니다. 실제 비밀번호로 재설정하면 파기됩니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "204",
          description = "성공"
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "비활성화된 엔드포인트 (이메일 발송 기능 검증 전까지 password-reset.enabled=false로 숨김 처리)",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  void resetPassword(
      @Valid @RequestBody ResetPasswordRequest request
  );

  @Operation(
      summary = "토큰 재발급",
      description = "쿠키에 담긴 Refresh Token으로 Access Token과 Refresh Token을 재발급합니다. "
          + "재발급 시 기존 Refresh Token은 무효화되고 새 값으로 교체됩니다(Rotation)."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = TokenResponse.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "Refresh Token이 없거나, 유효하지 않거나, 만료됨",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<TokenResponse> refresh(
      @Parameter(hidden = true)
      @CookieValue(value = "refreshToken", required = false) String refreshToken
  );

  @Operation(
      summary = "CSRF 토큰 발급",
      description = "CSRF 토큰을 쿠키(XSRF-TOKEN)로 발급합니다. "
          + "이후 상태 변경 요청 시 헤더(X-XSRF-TOKEN)에 이 값을 담아 전송해야 합니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공"
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<Void> csrfToken(
      @Parameter(hidden = true) CsrfToken csrfToken
  );
}