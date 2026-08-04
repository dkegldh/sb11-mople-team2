package com.codeit.mople.domain.notification.repository;

import com.codeit.mople.domain.notification.entity.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, NotificationRepositoryCustom {

    long countByReceiver_Id(UUID receiverId);
}