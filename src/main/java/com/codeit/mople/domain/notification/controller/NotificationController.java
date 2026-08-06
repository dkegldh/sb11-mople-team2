package com.codeit.mople.domain.notification.controller;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.notification.controller.api.NotificationApi;
import com.codeit.mople.domain.notification.dto.request.NotificationCursorRequest;
import com.codeit.mople.domain.notification.dto.response.CursorResponseNotificationDto;
import com.codeit.mople.domain.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @Override
    @GetMapping
    public ResponseEntity<CursorResponseNotificationDto> getNotifications(
        @AuthenticationPrincipal(errorOnInvalidType = true) CustomUserDetails userDetails,
        @Valid NotificationCursorRequest request
    ) {
        CursorResponseNotificationDto response = notificationService.getNotifications(
            userDetails.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
        @AuthenticationPrincipal(errorOnInvalidType = true) CustomUserDetails userDetails,
        @PathVariable UUID notificationId
    ) {
        notificationService.deleteNotification(notificationId, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}
