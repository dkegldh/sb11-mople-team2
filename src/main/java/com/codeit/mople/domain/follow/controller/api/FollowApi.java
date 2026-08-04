package com.codeit.mople.domain.follow.controller.api;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.dto.FollowResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "팔로우 관리")
public interface FollowApi {
  //
  @Operation(
      summary = "팔로우")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "성공",
          content = @Content(schema = @Schema(implementation = FollowResponse.class))),
      @ApiResponse(responseCode = "400", description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(responseCode = "500", description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
  })
  ResponseEntity<FollowResponse> createFollow(
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal,
      @Valid @RequestBody FollowRequest followRequest);

  //
  @Operation(
      summary = "팔로우 취소",
      description = "API 요청자 본인의 팔로우만 취소할 수 있습니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(responseCode = "403", description = "권한 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(responseCode = "500", description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
  })
  ResponseEntity<Void> cancelFollow(
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID followId);

  //
  @Operation(
      summary = "특정 유저를 내가 팔로우하는지 여부 조회",
      description = "팔로우 중이면 FollowResponse(id 포함)를 반환합니다. 팔로우하지 않은 경우 404를 반환합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "성공",
          content = @Content(schema = @Schema(implementation = FollowResponse.class))),
      @ApiResponse(responseCode = "400", description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(responseCode = "404", description = "해당 리소스 없음",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(responseCode = "500", description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
  })
  ResponseEntity<FollowResponse> isFollowedByMe(
      @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal,
      @RequestParam UUID followeeId);

  //
  @Operation(summary = "특정 유저의 팔로워 수 조회")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "성공",
          content = @Content(schema = @Schema(implementation = Long.class))),
      @ApiResponse(responseCode = "400", description = "잘못된 요청",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(responseCode = "401", description = "인증 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
      @ApiResponse(responseCode = "500", description = "서버 오류",
          content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))),
  })
  ResponseEntity<Long> getFollowerCount(
      @RequestParam UUID followeeId);
}
