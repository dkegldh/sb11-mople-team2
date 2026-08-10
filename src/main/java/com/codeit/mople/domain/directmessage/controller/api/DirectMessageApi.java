package com.codeit.mople.domain.directmessage.controller.api;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.dto.response.CursorResponseDirectMessageDto;
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

@Tag(
    name = "쪽지(DM) 관리",
    description = "특정 대화방 내부의 쪽지(DM) 관련 API"
)
public interface DirectMessageApi {

  @Operation(
      summary = "대화방 내부 메시지 목록 조회 (무한 스크롤)",
      description = "특정 대화방 내부에 쌓인 쪽지(DM) 목록을 최신순으로 페이징 조회합니다. 방에 처음 진입(첫 페이지 조회) 시 자동으로 해당 유저의 읽음 워터마크가 최신화됩니다."
  )
  @Parameters({
      @Parameter(name = "conversationId", description = "조회할 대화방의 고유 ID", in = ParameterIn.PATH, schema = @Schema(type = "string", format = "uuid")),
      @Parameter(name = "cursor", description = "페이징 커서 (이전 페이지 마지막 메시지의 createdAt 문자열)", in = ParameterIn.QUERY, schema = @Schema(type = "string")),
      @Parameter(name = "idAfter", description = "동시간 충돌 방지용 PK 커서 (cursor와 반드시 짝을 이루어야 함)", in = ParameterIn.QUERY, schema = @Schema(type = "string", format = "uuid")),
      @Parameter(name = "limit", description = "한 페이지에 조회할 메시지 개수 (기본값: 20)", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20")),
      @Parameter(name = "sortDirection", description = "정렬 방향 고정 ('DESCENDING')", in = ParameterIn.QUERY, schema = @Schema(type = "string", defaultValue = "DESCENDING")),
      @Parameter(name = "sortBy", description = "정렬 기준 컬럼 고정 ('createdAt')", in = ParameterIn.QUERY, schema = @Schema(type = "string", defaultValue = "createdAt"))
  })
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "성공",
          content = @Content(schema = @Schema(implementation = CursorResponseDirectMessageDto.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청 (커서 포맷 에러 또는 idAfter 짝 불일치)",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "403",
          description = "권한 없음 (요청자가 해당 대화방의 참여자가 아님)",
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
  ResponseEntity<CursorResponseDirectMessageDto> getDirectMessages(
      @Parameter(hidden = true) @PathVariable UUID conversationId,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid DirectMessageCursorRequest request
  );

  @Operation(
      summary = "단건 메시지 읽음 처리 (워터마크 갱신)",
      description = "대화방 내 특정 메시지 1건을 읽음 처리합니다. 내부적으로 해당 메시지의 발송 시각으로 요청 유저의 읽음 워터마크를 갱신하며, 그 이전의 메시지들은 일괄 읽음 처리되는 효과를 갖습니다."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "204",
          description = "성공"
      ),
      @ApiResponse(
          responseCode = "401",
          description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "403",
          description = "권한 없음 (요청 유저가 해당 메시지의 수신자가 아님)",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "메시지를 찾을 수 없음 (존재하지 않는 directMessageId이거나 해당 대화방 소속이 아님)",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      ),
      @ApiResponse(
          responseCode = "500",
          description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
      )
  })
  ResponseEntity<Void> readMessage(
      @PathVariable UUID conversationId,
      @PathVariable UUID directMessageId,
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
  );
}