package com.codeit.mople.domain.playlist.controller.api;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.mople.domain.playlist.dto.response.PlaylistResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
    name = "플레이리스트 관리",
    description = "플레이리스트 관련 API"
)
public interface PlaylistApi {

  @Operation(
      summary = "플레이리스트 생성",
      description = "생성한 플레이리스트는 API 요청자 본인의 플레이리스트로 생성됩니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "성공",
          content = @Content(schema = @Schema(implementation = PlaylistResponse.class))
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
          description = "권한 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      // TODO 김명근: 인증 구현 후 404 응답 삭제
      @ApiResponse(
          responseCode = "404",
          description = "플레이리스트 소유자를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<PlaylistResponse> create(
      @Parameter(hidden = true)
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody PlaylistCreateRequest request
  );

  @Operation(
      summary = "플레이리스트 구독"
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
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
  })
  ResponseEntity<Void> createSubscribe(
     @PathVariable UUID playlistId,
     @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal
  );
}
