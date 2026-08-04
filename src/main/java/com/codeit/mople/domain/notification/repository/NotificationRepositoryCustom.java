package com.codeit.mople.domain.notification.repository;

import com.codeit.mople.domain.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepositoryCustom {

    List<Notification> findNotificationByCursor(UUID receiverId, Instant cursorTime, UUID idAfter, int limit);
}
