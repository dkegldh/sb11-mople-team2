package com.codeit.mople.domain.notification.controller;

import com.codeit.mople.domain.notification.dto.request.NotificationCursorRequest;
import com.codeit.mople.domain.notification.dto.response.CursorResponseNotificationDto;
import com.codeit.mople.domain.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    // TODO: 보안 연동 완료 시 @AuthenticationPrincipal로 교체
    private static final UUID TEMP_RECEIVER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @GetMapping
    public ResponseEntity<CursorResponseNotificationDto> getNotifications(
        @Valid NotificationCursorRequest request
    ) {
        CursorResponseNotificationDto response = notificationService.getNotifications(
            TEMP_RECEIVER_ID, request);
        return ResponseEntity.ok(response);
    }
}
