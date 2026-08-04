package com.codeit.mople.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.mople.domain.notification.dto.request.NotificationCursorRequest;
import com.codeit.mople.domain.notification.entity.Notification;
import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@DisplayName("NotificationRepository 테스트")
class NotificationRepositoryTest {

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    TestEntityManager entityManager;

    User receiver;
    User otherUser;

    @BeforeEach
    void setUp() {
        receiver = entityManager.persist(User.createUser("receiver@test.com", "password", "수신자"));
        otherUser = entityManager.persist(User.createUser("other@test.com", "password", "타유저"));
    }

    private Notification 알림_저장(User user, String title, NotificationType type) {
        Notification notification = Notification.create(user, title, "내용", type);
        entityManager.persist(notification);
        entityManager.flush();
        UUID savedId = notification.getId();
        entityManager.clear();
        // DB에서 재조회해 실제 저장된 createdAt을 반영 (정밀도 차이 방지)
        return entityManager.find(Notification.class, savedId);
    }

    @Nested
    @DisplayName("알림 저장 [save]")
    class Save {

        @Test
        @DisplayName("저장 후 모든 필드와 createdAt이 조회된다")
        void 저장_후_필드값_확인() {
            Notification notification = Notification.create(receiver, "제목", "내용", NotificationType.NEW_FOLLOWER);

            Notification saved = notificationRepository.save(notification);
            entityManager.flush();
            entityManager.clear();

            Notification found = entityManager.find(Notification.class, saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getReceiver().getId()).isEqualTo(receiver.getId());
            assertThat(found.getTitle()).isEqualTo("제목");
            assertThat(found.getContent()).isEqualTo("내용");
            assertThat(found.getNotificationType()).isEqualTo(NotificationType.NEW_FOLLOWER);
            assertThat(found.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 receiver_id로 저장하면 FK 제약 위반 예외가 발생한다")
        void 존재하지_않는_receiver_저장시_예외() {
            UUID fakeReceiverId = UUID.randomUUID();

            assertThatThrownBy(() -> {
                entityManager.getEntityManager().createNativeQuery(
                    "INSERT INTO notifications (id, receiver_id, title, content, level, notification_type, created_at) " +
                    "VALUES (RANDOM_UUID(), :receiverId, '제목', '내용', 'INFO', 'NEW_FOLLOWER', NOW())"
                ).setParameter("receiverId", fakeReceiverId).executeUpdate();
                entityManager.flush();
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("content가 null이어도 DB에 저장된다")
        void content_null_저장_성공() {
            Notification notification = Notification.create(receiver, "제목", null, NotificationType.NEW_FOLLOWER);

            Notification saved = notificationRepository.save(notification);
            entityManager.flush();
            entityManager.clear();

            Notification found = entityManager.find(Notification.class, saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getContent()).isNull();
        }

        @Test
        @DisplayName("title이 null이면 DB nullable 제약으로 저장에 실패한다")
        void title_null_저장_실패() {
            assertThatThrownBy(() -> {
                entityManager.getEntityManager().createNativeQuery(
                    "INSERT INTO notifications (id, receiver_id, title, content, level, notification_type, created_at) " +
                    "VALUES (RANDOM_UUID(), :receiverId, NULL, '내용', 'INFO', 'NEW_FOLLOWER', NOW())"
                ).setParameter("receiverId", receiver.getId()).executeUpdate();
                entityManager.flush();
            }).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("수신자별 알림 조회 [findByReceiverIdOrderByCreatedAtDesc]")
    class FindByReceiverIdOrderByCreatedAtDesc {

        @Test
        @DisplayName("receiver의 알림만 조회된다")
        void receiver_알림만_조회됨() {
            알림_저장(receiver, "팔로우 알림", NotificationType.NEW_FOLLOWER);
            알림_저장(otherUser, "다른 유저 알림", NotificationType.PLAYLIST_SUBSCRIBE);

            List<Notification> result = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(receiver.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReceiver().getId()).isEqualTo(receiver.getId());
        }

        @Test
        @DisplayName("알림이 없으면 빈 리스트를 반환한다")
        void 알림_없으면_빈_리스트() {
            List<Notification> result = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(receiver.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("모든 알림 종류가 저장되고 조회된다")
        void 모든_알림_종류_조회됨() {
            알림_저장(receiver, "팔로우 알림", NotificationType.NEW_FOLLOWER);
            알림_저장(receiver, "권한 변경 알림", NotificationType.ROLE_CHANGE);
            알림_저장(receiver, "플레이리스트 구독 알림", NotificationType.PLAYLIST_SUBSCRIBE);
            알림_저장(receiver, "콘텐츠 추가 알림", NotificationType.PLAYLIST_CONTENT_ADDED);
            알림_저장(receiver, "팔로위 활동 알림", NotificationType.FOLLOWEE_ACTIVITY);
            알림_저장(receiver, "DM 알림", NotificationType.DIRECT_MESSAGE);

            List<Notification> result = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(receiver.getId());

            assertThat(result).hasSize(6);
            assertThat(result)
                .extracting(Notification::getNotificationType)
                .containsExactlyInAnyOrder(
                    NotificationType.NEW_FOLLOWER,
                    NotificationType.ROLE_CHANGE,
                    NotificationType.PLAYLIST_SUBSCRIBE,
                    NotificationType.PLAYLIST_CONTENT_ADDED,
                    NotificationType.FOLLOWEE_ACTIVITY,
                    NotificationType.DIRECT_MESSAGE
                );
        }

        @Test
        @DisplayName("알림이 최신순으로 정렬되어 조회된다")
        void 최신순_정렬_확인() {
            알림_저장(receiver, "첫 번째 알림", NotificationType.NEW_FOLLOWER);
            알림_저장(receiver, "두 번째 알림", NotificationType.PLAYLIST_SUBSCRIBE);

            List<Notification> result = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(receiver.getId());

            assertThat(result).hasSize(2);
            assertThat(result).isSortedAccordingTo(
                Comparator.comparing(Notification::getCreatedAt).reversed()
            );
        }
    }

    @Nested
    @DisplayName("알림 삭제 [deleteById]")
    class DeleteById {

        @Test
        @DisplayName("삭제 후 조회되지 않는다")
        void 삭제_후_조회_안됨() {
            Notification saved = 알림_저장(receiver, "팔로우 알림", NotificationType.NEW_FOLLOWER);

            notificationRepository.deleteById(saved.getId());
            entityManager.flush();
            entityManager.clear();

            List<Notification> result = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(receiver.getId());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("내 알림 삭제가 다른 receiver의 알림에 영향을 주지 않는다")
        void 다른_receiver_알림_영향_없음() {
            Notification myNotification = 알림_저장(receiver, "내 알림", NotificationType.NEW_FOLLOWER);
            알림_저장(otherUser, "타유저 알림", NotificationType.NEW_FOLLOWER);

            notificationRepository.deleteById(myNotification.getId());
            entityManager.flush();
            entityManager.clear();

            List<Notification> otherResult = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(otherUser.getId());
            assertThat(otherResult).hasSize(1);
        }
    }

    @Nested
    @DisplayName("커서 기반 알림 조회 [findNotificationByCursor]")
    class FindNotificationByCursor {

        private NotificationCursorRequest firstPageRequest() {
            return new NotificationCursorRequest(null, null, 20, "DESCENDING", "createdAt");
        }

        @Test
        @DisplayName("cursor 없이 조회하면 receiver의 알림만 최신순으로 반환된다.")
        void 첫_페이지_receiver_알림만_최신순_반환() {
            알림_저장(receiver, "알림1", NotificationType.NEW_FOLLOWER);
            알림_저장(receiver, "알림2", NotificationType.PLAYLIST_SUBSCRIBE);
            알림_저장(receiver, "알림3", NotificationType.DIRECT_MESSAGE);
            알림_저장(otherUser, "타유저 알림", NotificationType.NEW_FOLLOWER);

            List<Notification> result = notificationRepository.findNotificationByCursor(
                receiver.getId(), firstPageRequest(), null);

            // receiver 알림 3개만 반환 (otherUser 알림 제외)
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(n -> n.getReceiver().getId().equals(receiver.getId()));
            // 최신순 정렬 확인
            assertThat(result).isSortedAccordingTo(
                Comparator.comparing(Notification::getCreatedAt).reversed()
            );
        }

        @Test
        @DisplayName("알림이 없으면 빈 리스트를 반환한다.")
        void 알림_없으면_빈_리스트() {
            List<Notification> result = notificationRepository.findNotificationByCursor(
                receiver.getId(), firstPageRequest(), null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("알림이 limit+1개 초과 존재하면 최대 limit+1개까지만 반환된다.")
        void 알림이_limit_초과_시_limit_플러스_1개_반환() {
            for (int i = 0; i < 5; i++) {
                알림_저장(receiver, "알림" + i, NotificationType.NEW_FOLLOWER);
            }
            NotificationCursorRequest request = new NotificationCursorRequest(
                null, null, 3, "DESCENDING", "createdAt");

            List<Notification> result = notificationRepository.findNotificationByCursor(
                receiver.getId(), request, null);

            // limit=3이므로 limit+1=4개까지만 반환 (Service에서 hasNext 판별용)
            assertThat(result).hasSize(4);
        }

        @Test
        @DisplayName("알림이 limit개 이하이면 전체를 반환한다.")
        void 알림이_limit_이하이면_전체_반환() {
            알림_저장(receiver, "알림1", NotificationType.NEW_FOLLOWER);
            알림_저장(receiver, "알림2", NotificationType.PLAYLIST_SUBSCRIBE);
            NotificationCursorRequest request = new NotificationCursorRequest(
                null, null, 5, "DESCENDING", "createdAt");

            List<Notification> result = notificationRepository.findNotificationByCursor(
                receiver.getId(), request, null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("cursor 시각보다 오래된 알림만 반환되고 cursor 시각 이후 알림은 제외된다.")
        void cursor_이전_알림만_반환() throws InterruptedException {
            Notification older = 알림_저장(receiver, "오래된 알림", NotificationType.NEW_FOLLOWER);
            Thread.sleep(10); // 타임스탬프 차이 보장
            Notification newer = 알림_저장(receiver, "최신 알림", NotificationType.NEW_FOLLOWER);

            // newer의 createdAt을 cursor로 사용 → older만 반환되어야 함
            List<Notification> result = notificationRepository.findNotificationByCursor(
                receiver.getId(), firstPageRequest(), newer.getCreatedAt());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(older.getId());
            assertThat(result.get(0).getTitle()).isEqualTo("오래된 알림");
            // cursor 시각 이후인 newer는 포함되지 않아야 함
            assertThat(result).noneMatch(n -> n.getId().equals(newer.getId()));
        }

        @Test
        @DisplayName("cursor 이후에 저장된 알림이 여러 개여도 cursor 이전 알림만 반환된다.")
        void cursor_이후_알림_여러개_있어도_이전것만_반환() throws InterruptedException {
            Notification oldest = 알림_저장(receiver, "가장 오래된 알림", NotificationType.NEW_FOLLOWER);
            Thread.sleep(10);
            Notification middle = 알림_저장(receiver, "중간 알림", NotificationType.PLAYLIST_SUBSCRIBE);
            Thread.sleep(10);
            알림_저장(receiver, "최신 알림1", NotificationType.DIRECT_MESSAGE);
            알림_저장(receiver, "최신 알림2", NotificationType.ROLE_CHANGE);

            // middle의 createdAt을 cursor로 사용 → oldest만 반환되어야 함
            List<Notification> result = notificationRepository.findNotificationByCursor(
                receiver.getId(), firstPageRequest(), middle.getCreatedAt());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(oldest.getId());
        }
    }
}
