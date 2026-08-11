package com.codeit.mople.domain.content.controller.api;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.content.dto.ContentCreateRequest;
import com.codeit.mople.domain.content.dto.ContentResponse;
import com.codeit.mople.domain.content.dto.ContentUpdateRequest;
import com.codeit.mople.domain.content.dto.CursorResponseContentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(
    name = "콘텐츠 관리",
    description = "콘텐츠 관련 API"
)
public interface ContentApi {

  @Operation(
      summary = "콘텐츠 생성",
      description = "새로운 콘텐츠를 생성합니다(ADMIN 전용)"
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "생성 성공",
          content = @Content(schema = @Schema(implementation = ContentResponse.class))
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
      )
  })
  ResponseEntity<ContentResponse> createContent(
      @Valid @RequestPart("request") ContentCreateRequest request,
      @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
  );

  @Operation(
      summary = "콘텐츠 목록 조회",
      description = "커서 기반 페이지네이션으로 콘텐츠 목록을 조회합니다"
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = CursorResponseContentDto.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 파라미터 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<CursorResponseContentDto> getContents(
      @RequestParam(value = "cursorId", required = false) UUID cursorId,
      @RequestParam(value = "cursorCreatedAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursorCreatedAt,
      @RequestParam(value = "limit", defaultValue = "10") int limit
  );

  @Operation(
      summary = "콘텐츠 단건 조회",
      description = "콘텐츠 ID를 통해 상세 정보를 조회합니다"
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = ContentResponse.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "콘텐츠를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<ContentResponse> getContent(
      @PathVariable UUID contentId
  );

  @Operation(
      summary = "콘텐츠 수정",
      description = "기존 콘텐츠 정보를 수정합니다(ADMIN 전용)"
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "수정 성공",
          content = @Content(schema = @Schema(implementation = ContentResponse.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "403",
          description = "권한 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "콘텐츠를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<ContentResponse> updateContent(
      @PathVariable UUID contentId,
      @Valid @RequestPart("request") ContentUpdateRequest request,
      @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail
  );

  @Operation(
      summary = "콘텐츠 삭제",
      description = "콘텐츠를 삭제합니다(ADMIN 전용)"
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "204",
          description = "삭제 성공"
      ),
      @ApiResponse(
          responseCode = "403",
          description = "권한 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<Void> deleteContent(
      @PathVariable UUID contentId
  );
}