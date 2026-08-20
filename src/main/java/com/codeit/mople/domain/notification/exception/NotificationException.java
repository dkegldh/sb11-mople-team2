package com.codeit.mople.domain.notification.exception;

import com.codeit.mople.global.error.CustomException;
import java.util.Map;

public class NotificationException extends CustomException {

    public NotificationException(NotificationErrorCode errorCode) {
        super(errorCode);
    }

    public NotificationException(NotificationErrorCode errorCode, Map<String, Object> details) {
        super(errorCode, details);
    }
}
