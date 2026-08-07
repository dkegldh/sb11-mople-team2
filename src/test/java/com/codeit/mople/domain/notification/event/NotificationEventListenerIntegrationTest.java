package com.codeit.mople.domain.notification.event;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.repository.FollowRepository;
import com.codeit.mople.domain.follow.service.FollowService;
import com.codeit.mople.domain.notification.entity.Notification;
import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import com.codeit.mople.domain.user.admin.service.AdminService;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.event.ForceLogoutReason;
import com.codeit.mople.global.event.UserForceLogoutEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@DisplayName("NotificationEventListener 통합 테스트")
class NotificationEventListenerIntegrationTest {

    @Autowired private AdminService adminService;
    @Autowired private FollowService followService;
    @Autowired private FollowRepository followRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;

    UUID targetUserId;

    @BeforeEach
    void setUp() {
        UUID adminId = UUID.randomUUID();
        User target = userRepository.save(User.createUser("target@test.com", "encoded", "대상유저"));
        targetUserId = target.getId();

        CustomUserDetails principal = new CustomUserDetails(adminId, Role.ADMIN);
        Authentication auth = new UsernamePasswordAuthenticationToken(
            principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        followRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("권한 변경 트랜잭션 커밋 후 ROLE_CHANGE 알림이 저장된다")
    void 권한_변경_트랜잭션_커밋_후_ROLE_CHANGE_알림이_저장된다() {
        adminService.changeUserRole(targetUserId, "ADMIN");

        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getNotificationType()).isEqualTo(NotificationType.ROLE_CHANGE);
        });
    }

    @Test
    @DisplayName("계정 잠금 트랜잭션 커밋 후 ACCOUNT_LOCKED 알림이 저장된다")
    void 계정_잠금_트랜잭션_커밋_후_ACCOUNT_LOCKED_알림이_저장된다() {
        adminService.changeUserLocked(targetUserId, true);

        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getNotificationType()).isEqualTo(NotificationType.ACCOUNT_LOCKED);
        });
    }

    @Test
    @DisplayName("계정 잠금 해제 트랜잭션 커밋 후 ACCOUNT_UNLOCKED 알림이 저장되고 sessionVersion은 변경되지 않는다")
    void 계정_잠금_해제_트랜잭션_커밋_후_ACCOUNT_UNLOCKED_알림이_저장되고_sessionVersion은_변경되지_않는다() {
        User target = userRepository.findById(targetUserId).orElseThrow();
        target.lock();
        userRepository.save(target);

        adminService.changeUserLocked(targetUserId, false);

        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getNotificationType()).isEqualTo(NotificationType.ACCOUNT_UNLOCKED);
        });

        assertThat(userRepository.findById(targetUserId).orElseThrow().getSessionVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("팔로우 트랜잭션 커밋 후 NEW_FOLLOWER 알림이 followee에게 저장된다")
    void 팔로우_트랜잭션_커밋_후_NEW_FOLLOWER_알림이_followee에게_저장된다() {
        // given
        User follower = userRepository.save(User.createUser("follower@test.com", "encoded", "팔로워유저"));

        // when
        followService.follow(new FollowRequest(targetUserId), follower.getId());

        // then
        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(1);
            Notification notification = notifications.get(0);
            assertThat(notification.getNotificationType()).isEqualTo(NotificationType.NEW_FOLLOWER);
            assertThat(notification.getReceiver().getId()).isEqualTo(targetUserId);
            assertThat(notification.getTitle()).isEqualTo("새로운 팔로워가 생겼습니다.");
            assertThat(notification.getContent()).isEqualTo("팔로워유저님이 팔로우했습니다.");
        });
    }

    @Test
    @DisplayName("이벤트 발행 후 트랜잭션 롤백 시 AFTER_COMMIT 리스너가 실행되지 않아 알림이 저장되지 않는다")
    void 이벤트_발행_후_트랜잭션_롤백_시_알림이_저장되지_않는다() {
        transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            eventPublisher.publishEvent(new UserForceLogoutEvent(targetUserId, ForceLogoutReason.ROLE_CHANGE));
            return null;
        });

        // 롤백 후 비동기 리스너가 실행되지 않았음을 확인 (500ms 대기 후 검증)
        await().pollDelay(500, MILLISECONDS).atMost(1, SECONDS)
            .untilAsserted(() -> assertThat(notificationRepository.countByReceiver_Id(targetUserId)).isZero());
    }
}
