package com.codeit.mople.domain.notification.repository;

import com.codeit.mople.domain.notification.entity.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, NotificationRepositoryCustom {

    @Query("SELECT n FROM Notification n JOIN FETCH n.receiver WHERE n.receiver.id = :receiverId ORDER BY n.createdAt DESC")
    List<Notification> findByReceiverIdOrderByCreatedAtDesc(@Param("receiverId") UUID receiverId);
}