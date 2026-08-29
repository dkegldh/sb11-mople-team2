package com.codeit.mople.domain.watchingsession.controller.api;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.watchingsession.dto.CursorResponseWatchingSessionDto;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(
    name = "시청 세션 관리",
    description = "Redis 기반 실시간 콘텐츠 시청자 관리 API"
)
public interface WatchingSessionApi {

  @Operation(
      summary = "특정 유저 시청 세션 단건 조회",
      description = "특정 유저가 현재 실시간으로 시청 중인 세션을 콘텐츠 정보와 함께 조회합니다. 시청 중이 아니면 본문이 비어 있습니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공 (시청 중이 아니면 본문 없음)",
          content = @Content(schema = @Schema(implementation = WatchingSessionResponse.class))
      )
  })
  ResponseEntity<WatchingSessionResponse> getWatchingSessionForUser(
      @PathVariable UUID watcherId
  );

  @Operation(
      summary = "콘텐츠 실시간 시청자 목록 조회",
      description = "특정 콘텐츠를 실시간으로 시청 중인 유저 목록을 커서 기반 페이징으로 조회합니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "조회 성공",
          content = @Content(schema = @Schema(implementation = CursorResponseWatchingSessionDto.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 파라미터 요청 (limit 범위 초과, 잘못된 정렬 기준 등)"
      ),
      @ApiResponse(
          responseCode = "404",
          description = "콘텐츠를 찾을 수 없음"
      )
  })
  ResponseEntity<CursorResponseWatchingSessionDto> getWatchingSessions(
      @PathVariable UUID contentId,
      @RequestParam(value = "watcherNameLike", required = false) String watcherNameLike,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "idAfter", required = false) UUID idAfter,
      @RequestParam(value = "limit", defaultValue = "10") int limit,
      @RequestParam(value = "sortDirection", defaultValue = "ASCENDING") String sortDirection,
      @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
      @AuthenticationPrincipal CustomUserDetails userDetails
  );
}