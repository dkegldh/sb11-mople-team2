package com.codeit.mople.domain.user.admin.controller.api;

import com.codeit.mople.domain.user.admin.dto.LockUpdateRequest;
import com.codeit.mople.domain.user.admin.dto.RoleUpdateRequest;
import com.codeit.mople.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "어드민 관리")
public interface AdminApi {

  @Operation(operationId = "changeUserRole", summary = "사용자 권한 수정")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 권한 값",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
  })
  ApiResponse<Void> changeRole(
      @PathVariable UUID userId,
      @Valid @RequestBody RoleUpdateRequest request);

  @Operation(operationId = "changeUserLocked", summary = "계정 잠금 상태 변경")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
  })
  ApiResponse<Void> changeLocked(
      @PathVariable UUID userId,
      @Valid @RequestBody LockUpdateRequest request);
}
