package com.codeit.mople.domain.notification.entity;

import com.codeit.mople.domain.notification.NotificationLevel;
import com.codeit.mople.domain.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private User receiver;

    @BeforeEach
    void setUp() {
        receiver = User.createUser("test@test.com", "encodedPassword", "testUser");
    }

    @Test
    @DisplayName("Notification 생성 시 전달한 필드값이 저장된다")
    void create_success() {
        Notification notification = Notification.create(receiver, "제목", "내용", NotificationType.NEW_FOLLOWER);

        assertThat(notification.getReceiver()).isEqualTo(receiver);
        assertThat(notification.getTitle()).isEqualTo("제목");
        assertThat(notification.getContent()).isEqualTo("내용");
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.NEW_FOLLOWER);
    }

    @Test
    @DisplayName("content가 null이어도 Notification 생성에 성공한다")
    void create_success_withNullContent() {
        Notification notification = Notification.create(receiver, "제목", null, NotificationType.NEW_FOLLOWER);

        assertThat(notification.getContent()).isNull();
    }

    @Test
    @DisplayName("ROLE_CHANGE, ACCOUNT_LOCKED 타입은 level이 WARNING이다")
    void create_levelIsWarning_whenWarningType() {
        for (NotificationType type : List.of(NotificationType.ROLE_CHANGE, NotificationType.ACCOUNT_LOCKED)) {
            Notification notification = Notification.create(receiver, "제목", "내용", type);

            assertThat(notification.getLevel())
                .as("NotificationType=%s 일 때 level은 WARNING이어야 한다", type)
                .isEqualTo(NotificationLevel.WARNING);
        }
    }

    @Test
    @DisplayName("ROLE_CHANGE, ACCOUNT_LOCKED 외 타입은 level이 INFO이다")
    void create_levelIsInfo_whenInfoType() {
        for (NotificationType type : NotificationType.values()) {
            if (type == NotificationType.ROLE_CHANGE || type == NotificationType.ACCOUNT_LOCKED) continue;

            Notification notification = Notification.create(receiver, "제목", "내용", type);

            assertThat(notification.getLevel())
                .as("NotificationType=%s 일 때 level은 INFO여야 한다", type)
                .isEqualTo(NotificationLevel.INFO);
        }
    }

    @Test
    @DisplayName("receiver가 null이면 예외가 발생한다")
    void create_throwsException_whenReceiverIsNull() {
        assertThatThrownBy(() -> Notification.create(null, "제목", "내용", NotificationType.NEW_FOLLOWER))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("receiver");
    }

    @Test
    @DisplayName("title이 null이면 예외가 발생한다")
    void create_throwsException_whenTitleIsNull() {
        assertThatThrownBy(() -> Notification.create(receiver, null, "내용", NotificationType.NEW_FOLLOWER))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("title");
    }

    @Test
    @DisplayName("notificationType이 null이면 예외가 발생한다")
    void create_throwsException_whenNotificationTypeIsNull() {
        assertThatThrownBy(() -> Notification.create(receiver, "제목", "내용", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("notificationType");
    }
}
