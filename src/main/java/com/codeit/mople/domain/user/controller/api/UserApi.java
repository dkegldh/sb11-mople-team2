package com.codeit.mople.domain.user.controller.api;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mople.domain.user.dto.request.UserCreateRequest;
import com.codeit.mople.domain.user.dto.request.UserSearchRequest;
import com.codeit.mople.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mople.domain.user.dto.response.UserDto;
import com.codeit.mople.global.dto.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(
    name = "사용자 관리",
    description = "회원가입, 조회, 프로필/비밀번호 수정 등 사용자 관련 API"
)
public interface UserApi {

  @Operation(
      summary = "회원가입",
      description = "이메일, 비밀번호, 이름으로 회원가입을 합니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "성공",
          content = @Content(schema = @Schema(implementation = UserDto.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "409",
          description = "이메일 중복",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<UserDto> signUp(
      @RequestBody UserCreateRequest request
  );

  @Operation(
      summary = "사용자 조회",
      description = "userId로 특정 사용자의 정보를 조회합니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = UserDto.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "사용자를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<UserDto> getUser(
      @PathVariable UUID userId
  );

  @Operation(
      summary = "사용자 목록 조회(어드민 전용)",
      description = "이메일/권한/잠금상태 필터링과 커서 기반 페이지네이션을 지원합니다"
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = CursorResponse.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "403",
          description = "권한 없음 (어드민 전용)",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<CursorResponse<UserDto>> getUsers(
      UserSearchRequest request
  );

  @Operation(
      summary = "프로필 변경",
      description = "본인의 이름과 프로필 이미지를 변경합니다. 각 필드는 선택적으로 전달 가능합니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = UserDto.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "403",
          description = "본인이 아님",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "사용자를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<UserDto> updateProfile(
      @PathVariable UUID userId,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal,
      @Valid @RequestPart UserUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image
  );

  @Operation(
      summary = "비밀번호 변경",
      description = "본인의 비밀번호만 변경할 수 있습니다."
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
          responseCode = "401",
          description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "403",
          description = "본인이 아님",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "사용자를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<Void> changePassword(
      @PathVariable UUID userId,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal,
      @RequestBody ChangePasswordRequest request
  );
}
