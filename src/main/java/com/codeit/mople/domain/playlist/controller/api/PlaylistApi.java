package com.codeit.mople.domain.playlist.controller.api;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition;
import com.codeit.mople.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.mople.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.mople.global.dto.CursorResponse;
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
import org.springframework.web.bind.annotation.ModelAttribute;
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
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<PlaylistResponse> create(
      @RequestBody PlaylistCreateRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
  );

  @Operation(summary = "플레이리스트 단건 조회")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "플레이리스트 조회 성공",
          content = @Content(schema = @Schema(implementation = PlaylistResponse.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "인증되지 않은 사용자",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "플레이리스트를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<PlaylistResponse> find(
      @PathVariable UUID playlistId,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
  );

  @Operation(summary = "플레이리스트 목록 조회 (커서 페이지네이션)")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "플레이리스트 목록 조회 성공",
          content = @Content(
              schema = @Schema(implementation = CursorResponse.class)
          )
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
      )
  })
  ResponseEntity<CursorResponse<PlaylistResponse>> findAll(
      @ModelAttribute PlaylistQueryCondition condition,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
  );

  @Operation(
      summary = "플레이리스트 수정",
      description = "플레이리스트 소유자만 수정할 수 있습니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "플레이리스트 수정 성공",
          content = @Content(schema = @Schema(implementation = PlaylistResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "플레이리스트 수정 권한 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "플레이리스트를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class)))
  })
  ResponseEntity<PlaylistResponse> update(
      @PathVariable UUID playlistId,
      @RequestBody PlaylistUpdateRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
  );

  @Operation(
      summary = "플레이리스트 삭제",
      description = "플레이리스트 소유자만 삭제할 수 있습니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "204",
          description = "플레이리스트 삭제 성공"),
      @ApiResponse(
          responseCode = "401",
          description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "플레이리스트 삭제 권한 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "플레이리스트를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class)))
  })
  ResponseEntity<Void> delete(
      @PathVariable UUID playlistId,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
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

  @Operation(
      summary = "플레이리스트 구독 취소"
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
  ResponseEntity<Void> cancelSubscribe(
      @PathVariable UUID playlistId,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal
  );

  @Operation(
      summary = "플레이리스트 콘텐츠 추가",
      description = "플레이리스트 소유자만 콘텐츠를 추가할 수 있습니다."
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
          description = "권한 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
  })
  ResponseEntity<Void> addContentPlaylist(
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal
  );

  @Operation(
      summary = "플레이리스트 콘텐츠 삭제",
      description = "플레이리스트 소유자만 콘텐츠를 삭제할 수 있습니다."
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
          description = "권한 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
  })
  ResponseEntity<Void> removeContentPlaylist(
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal
  );
}