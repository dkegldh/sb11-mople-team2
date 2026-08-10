package com.codeit.mople.domain.conversation.controller.api;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.conversation.dto.request.ConversationCreateRequest;
import com.codeit.mople.domain.conversation.dto.request.ConversationCursorRequest;
import com.codeit.mople.domain.conversation.dto.response.ConversationDto;
import com.codeit.mople.domain.conversation.dto.response.CursorResponseConversationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(
    name = "대화방 관리",
    description = "1:1 대화방 관련 API"
)
public interface ConversationApi {

  @Operation(
      summary = "대화방 생성 및 조회",
      description = "지정한 유저와의 1:1 대화방을 생성합니다. 이미 존재하는 대화방이 있다면 기존 방을 반환합니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "성공",
          content = @Content(schema = @Schema(implementation = ConversationDto.class))
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
          responseCode = "404",
          description = "존재하지 않는 사용자",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<ConversationDto> createConversation(
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody ConversationCreateRequest request
  );

  @Operation(
      summary = "내 대화방 목록 조회 (무한 스크롤)",
      description = "내가 참여 중인 모든 대화방 목록을 최신 메시지 발송 시간 순서로 페이징 조회합니다."
  )
  @Parameters({
      @Parameter(name = "keywordLike", description = "상대방 닉네임 또는 메시지 내용 검색어", in = ParameterIn.QUERY, schema = @Schema(type = "string")),
      @Parameter(name = "cursor", description = "페이징 커서 (이전 페이지 마지막 항목의 lastMessageAt 문자열)", in = ParameterIn.QUERY, schema = @Schema(type = "string")),
      @Parameter(name = "idAfter", description = "동시간 충돌 방지용 PK 커서 (cursor와 짝을 이루어 전송 필요)", in = ParameterIn.QUERY, schema = @Schema(type = "string", format = "uuid")),
      @Parameter(name = "limit", description = "한 페이지에 조회할 개수 (기본값: 20)", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20")),
      @Parameter(name = "sortDirection", description = "정렬 방향 고정 ('DESCENDING')", in = ParameterIn.QUERY, schema = @Schema(type = "string", defaultValue = "DESCENDING")),
      @Parameter(name = "sortBy", description = "정렬 기준 컬럼 고정 ('createdAt')", in = ParameterIn.QUERY, schema = @Schema(type = "string", defaultValue = "createdAt"))
  })
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = CursorResponseConversationDto.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청 (커서 바인딩 혹은 짝 검증 실패)",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "존재하지 않는 사용자",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<CursorResponseConversationDto> findConversations(
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid ConversationCursorRequest request
  );

  @Operation(
      summary = "상대 유저 기준 대화방 조회",
      description = "상대방의 유저 ID를 활용하여 기존에 생성된 대화방 단건 정보를 조회합니다. 프로필 뷰에서 접근 시 사용됩니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = ConversationDto.class))
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
          responseCode = "404",
          description = "대화방을 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<ConversationDto> findConversationWithUser(
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam UUID userId
  );

  @Operation(
      summary = "대화방 단건 상세 조회",
      description = "대화방 고유 ID를 기반으로 대화방 정보를 조회합니다. 특정 채팅방 뷰 진입 시 사용됩니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = ConversationDto.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "403",
          description = "권한 없음 (대화방 참여자가 아님)",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "대화방을 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<ConversationDto> findConversation(
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable UUID conversationId
  );
}