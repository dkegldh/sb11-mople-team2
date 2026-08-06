package com.codeit.mople.domain.notification.controller.api;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.notification.dto.request.NotificationCursorRequest;
import com.codeit.mople.domain.notification.dto.response.CursorResponseNotificationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(
    name = "알림 관리",
    description = "알림 관련 API"
)
public interface NotificationApi {

    @Operation(
        summary = "알림 목록 커서 조회",
        description = "커서 기반 페이지네이션으로 알림 목록을 조회합니다. "
            + "cursor와 idAfter는 반드시 함께 제공하거나 둘 다 생략해야 합니다."
    )
    @Parameters({
        @Parameter(name = "cursor", description = "이전 페이지 마지막 항목의 createdAt (ISO-8601 형식). idAfter와 함께 사용"),
        @Parameter(name = "idAfter", description = "이전 페이지 마지막 항목의 ID. cursor와 함께 사용"),
        @Parameter(name = "limit", description = "페이지 크기 (1~100)", required = true),
        @Parameter(name = "sortDirection", description = "정렬 방향. 기본값: DESCENDING", schema = @Schema(allowableValues = {"DESCENDING"})),
        @Parameter(name = "sortBy", description = "정렬 기준. 기본값: createdAt", schema = @Schema(allowableValues = {"createdAt"}))
    })
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "성공",
            content = @Content(schema = @Schema(implementation = CursorResponseNotificationDto.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (cursor/idAfter 불일치, limit 범위 초과, 유효하지 않은 cursor 형식 등)",
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
    ResponseEntity<CursorResponseNotificationDto> getNotifications(
        @Parameter(hidden = true) @org.springframework.security.core.annotation.AuthenticationPrincipal CustomUserDetails userDetails,
        NotificationCursorRequest request);

    @Operation(summary = "알림 삭제", description = "알림을 삭제합니다. 본인 알림만 삭제할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(
            responseCode = "401",
            description = "인증 오류",
            content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "본인 알림이 아닌 경우",
            content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "알림을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = com.codeit.mople.global.response.ApiResponse.class))
        )
    })
    ResponseEntity<Void> deleteNotification(
        @Parameter(hidden = true) @org.springframework.security.core.annotation.AuthenticationPrincipal CustomUserDetails userDetails,
        UUID notificationId);
}
