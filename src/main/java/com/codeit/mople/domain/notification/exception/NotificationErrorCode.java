package com.codeit.mople.domain.notification.exception;

import com.codeit.mople.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION-001", "알림을 찾을 수 없습니다."),
    NOTIFICATION_FORBIDDEN(HttpStatus.FORBIDDEN, "NOTIFICATION-002", "해당 알림에 대한 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
