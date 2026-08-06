package com.codeit.mople.domain.notification.event;

import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.service.NotificationService;
import com.codeit.mople.global.event.UserForceLogoutEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserForceLogout(UserForceLogoutEvent event) {
        log.debug("강제 로그아웃 알림 처리 시작 - userId: {}, reason: {}", event.userId(), event.reason());

        String title = switch (event.reason()) {
            case ROLE_CHANGE -> "권한이 변경되었습니다.";
            case ACCOUNT_LOCKED -> "계정이 잠금되었습니다.";
            case ACCOUNT_UNLOCKED -> "계정 잠금이 해제되었습니다.";
        };
        String content = switch (event.reason()) {
            case ROLE_CHANGE -> "관리자에 의해 권한이 변경되었습니다. 다시 로그인해주세요.";
            case ACCOUNT_LOCKED -> "관리자에 의해 계정이 잠금되었습니다.";
            case ACCOUNT_UNLOCKED -> "관리자에 의해 계정 잠금이 해제되었습니다. 다시 로그인해주세요.";
        };
        NotificationType type = switch (event.reason()) {
            case ROLE_CHANGE -> NotificationType.ROLE_CHANGE;
            case ACCOUNT_LOCKED -> NotificationType.ACCOUNT_LOCKED;
            case ACCOUNT_UNLOCKED -> NotificationType.ACCOUNT_UNLOCKED;
        };

        notificationService.createNotification(event.userId(), title, content, type);
    }
}
