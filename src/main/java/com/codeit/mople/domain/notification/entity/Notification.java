package com.codeit.mople.domain.notification.entity;

import com.codeit.mople.domain.notification.NotificationLevel;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications",
    indexes = @Index(name = "idx_notifications_receiver_id", columnList = "receiver_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType notificationType;

    private Notification(User receiver, String title, String content,
        NotificationLevel level, NotificationType notificationType) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.title = Objects.requireNonNull(title, "title");
        this.content = content;
        this.level = Objects.requireNonNull(level, "level");
        this.notificationType = Objects.requireNonNull(notificationType, "notificationType");
    }

    public static Notification create(User receiver, String title, String content,
        NotificationType notificationType) {
        Objects.requireNonNull(notificationType, "notificationType");
        NotificationLevel level = resolveLevel(notificationType);
        return new Notification(receiver, title, content, level, notificationType);
    }

    private static NotificationLevel resolveLevel(NotificationType type) {
        return switch (type) {
            case ROLE_CHANGE, ACCOUNT_LOCKED -> NotificationLevel.WARNING;
            default -> NotificationLevel.INFO;
        };
    }
}