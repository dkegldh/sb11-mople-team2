package com.codeit.mople.domain.notification.event;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.repository.FollowRepository;
import com.codeit.mople.domain.follow.service.FollowService;
import com.codeit.mople.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.mople.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mople.domain.review.repository.ReviewRepository;
import com.codeit.mople.domain.review.service.ReviewService;
import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.mople.domain.playlist.repository.PlaylistRepository;
import com.codeit.mople.domain.playlist.repository.PlaylistSubscriptionRepository;
import com.codeit.mople.domain.playlist.service.PlaylistService;
import com.codeit.mople.domain.directmessage.repository.DirectMessageRepository;
import com.codeit.mople.domain.directmessage.service.DirectMessageService;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired private PlaylistService playlistService;
    @Autowired private PlaylistRepository playlistRepository;
    @Autowired private PlaylistSubscriptionRepository playlistSubscriptionRepository;
    @Autowired private PlaylistContentRepository playlistContentRepository;
    @Autowired private ContentRepository contentRepository;
    @Autowired private DirectMessageService directMessageService;
    @Autowired private DirectMessageRepository directMessageRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ReviewService reviewService;
    @Autowired private ReviewRepository reviewRepository;
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
        jdbcTemplate.execute("UPDATE conversations SET last_message_id = NULL");
        directMessageRepository.deleteAll();
        conversationRepository.deleteAll();
        followRepository.deleteAll();
        playlistSubscriptionRepository.deleteAll();
        playlistContentRepository.deleteAll();
        playlistRepository.deleteAll();
        reviewRepository.deleteAll();
        contentRepository.deleteAll();
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
    @DisplayName("DM 발송 트랜잭션 커밋 후 DIRECT_MESSAGE 알림이 수신자에게 저장된다")
    void DM_발송_트랜잭션_커밋_후_DIRECT_MESSAGE_알림이_수신자에게_저장된다() {
        // given
        User sender = userRepository.save(User.createUser("sender@test.com", "encoded", "발신자"));
        User receiver = userRepository.findById(targetUserId).orElseThrow();
        Conversation conversation = conversationRepository.save(Conversation.createConversation(sender, receiver));

        // when
        directMessageService.sendMessage(conversation.getId(), sender.getId(), "안녕하세요!");

        // then
        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(1);
            Notification notification = notifications.get(0);
            assertThat(notification.getNotificationType()).isEqualTo(NotificationType.DIRECT_MESSAGE);
            assertThat(notification.getReceiver().getId()).isEqualTo(targetUserId);
            assertThat(notification.getTitle()).isEqualTo("새로운 메시지가 도착했습니다.");
            assertThat(notification.getContent()).isEqualTo("발신자: 안녕하세요!");
        });
    }

    @Test
    @DisplayName("플레이리스트 콘텐츠 추가 트랜잭션 커밋 후 PLAYLIST_CONTENT_ADDED 알림이 구독자 전원에게 저장된다")
    void 플레이리스트_콘텐츠_추가_트랜잭션_커밋_후_PLAYLIST_CONTENT_ADDED_알림이_구독자_전원에게_저장된다() {
        // given
        User owner = userRepository.findById(targetUserId).orElseThrow();
        User subscriberA = userRepository.save(User.createUser("subA@test.com", "encoded", "구독자A"));
        User subscriberB = userRepository.save(User.createUser("subB@test.com", "encoded", "구독자B"));
        Playlist playlist = playlistRepository.save(Playlist.create(owner, "테스트 플레이리스트", "설명"));
        Content content = contentRepository.save(new Content(ContentType.MOVIE, "테스트 영화", null, null, null));
        playlistService.subscribe(playlist.getId(), subscriberA.getId());
        playlistService.subscribe(playlist.getId(), subscriberB.getId());
        await().atMost(3, SECONDS).until(() -> notificationRepository.count() >= 2);
        notificationRepository.deleteAll(); // 구독 알림 제거

        // when
        playlistService.addContent(playlist.getId(), content.getId(), targetUserId);

        // then
        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(2);
            assertThat(notifications)
                .extracting(n -> n.getReceiver().getId())
                .containsExactlyInAnyOrder(subscriberA.getId(), subscriberB.getId());
            assertThat(notifications)
                .allMatch(n -> n.getNotificationType() == NotificationType.PLAYLIST_CONTENT_ADDED);
            assertThat(notifications)
                .allMatch(n -> n.getContent().equals("테스트 플레이리스트에 새 콘텐츠가 추가되었습니다."));
        });
    }

    @Test
    @DisplayName("플레이리스트 구독 트랜잭션 커밋 후 PLAYLIST_SUBSCRIBE 알림이 owner에게 저장된다")
    void 플레이리스트_구독_트랜잭션_커밋_후_PLAYLIST_SUBSCRIBE_알림이_owner에게_저장된다() {
        // given
        User owner = userRepository.findById(targetUserId).orElseThrow();
        User subscriber = userRepository.save(User.createUser("subscriber@test.com", "encoded", "구독자유저"));
        Playlist playlist = playlistRepository.save(Playlist.create(owner, "테스트 플레이리스트", "설명"));

        // when
        playlistService.subscribe(playlist.getId(), subscriber.getId());

        // then
        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(1);
            Notification notification = notifications.get(0);
            assertThat(notification.getNotificationType()).isEqualTo(NotificationType.PLAYLIST_SUBSCRIBE);
            assertThat(notification.getReceiver().getId()).isEqualTo(targetUserId);
            assertThat(notification.getTitle()).isEqualTo("플레이리스트에 새 구독자가 생겼습니다.");
            assertThat(notification.getContent()).isEqualTo("구독자유저님이 테스트 플레이리스트을(를) 구독했습니다.");
        });
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
    @DisplayName("플레이리스트 생성 트랜잭션 커밋 후 FOLLOWEE_ACTIVITY 알림이 팔로워 전원에게 저장된다")
    void 플레이리스트_생성_트랜잭션_커밋_후_FOLLOWEE_ACTIVITY_알림이_팔로워_전원에게_저장된다() {
        // given
        User creator = userRepository.findById(targetUserId).orElseThrow();
        User followerA = userRepository.save(User.createUser("fA@test.com", "encoded", "팔로워A"));
        User followerB = userRepository.save(User.createUser("fB@test.com", "encoded", "팔로워B"));
        followService.follow(new FollowRequest(creator.getId()), followerA.getId());
        followService.follow(new FollowRequest(creator.getId()), followerB.getId());
        await().atMost(3, SECONDS).until(() -> notificationRepository.count() >= 2);
        notificationRepository.deleteAll(); // 팔로우 알림 제거

        // when
        playlistService.create(new PlaylistCreateRequest("새 플레이리스트", "설명"), creator.getId());

        // then
        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(2);
            assertThat(notifications)
                .extracting(n -> n.getReceiver().getId())
                .containsExactlyInAnyOrder(followerA.getId(), followerB.getId());
            assertThat(notifications)
                .allMatch(n -> n.getNotificationType() == NotificationType.FOLLOWEE_ACTIVITY);
            assertThat(notifications)
                .allMatch(n -> n.getTitle().equals(creator.getName() + "님의 새로운 활동이 있습니다."));
            assertThat(notifications)
                .allMatch(n -> n.getContent().equals("새 플레이리스트를 만들었습니다."));
        });
    }

    @Test
    @DisplayName("리뷰 작성 트랜잭션 커밋 후 FOLLOWEE_ACTIVITY 알림이 팔로워 전원에게 저장된다")
    void 리뷰_작성_트랜잭션_커밋_후_FOLLOWEE_ACTIVITY_알림이_팔로워_전원에게_저장된다() {
        // given
        User author = userRepository.findById(targetUserId).orElseThrow();
        User follower = userRepository.save(User.createUser("follower@test.com", "encoded", "팔로워유저"));
        followService.follow(new FollowRequest(author.getId()), follower.getId());
        await().atMost(3, SECONDS).until(() -> notificationRepository.count() >= 1);
        notificationRepository.deleteAll(); // 팔로우 알림 제거
        Content content = contentRepository.save(new Content(ContentType.MOVIE, "테스트 영화", null, null, null));

        // when
        reviewService.create(author.getId(), new ReviewCreateRequest(content.getId(), "좋은 영화입니다.", 4.5));

        // then
        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(1);
            Notification notification = notifications.get(0);
            assertThat(notification.getNotificationType()).isEqualTo(NotificationType.FOLLOWEE_ACTIVITY);
            assertThat(notification.getReceiver().getId()).isEqualTo(follower.getId());
            assertThat(notification.getTitle()).isEqualTo(author.getName() + "님의 새로운 활동이 있습니다.");
            assertThat(notification.getContent()).isEqualTo("리뷰를 작성했습니다.");
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
