package com.codeit.mople.domain.notification.event;

import com.codeit.mople.domain.directmessage.event.DirectMessageReceivedEvent;
import com.codeit.mople.domain.follow.event.FollowCreatedEvent;
import com.codeit.mople.domain.follow.service.FollowService;
import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.service.NotificationService;
import com.codeit.mople.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.mople.domain.review.event.ReviewWrittenEvent;
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
    private final FollowService followService;

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
            case ACCOUNT_UNLOCKED -> "관리자에 의해 계정 잠금이 해제되었습니다.";
        };
        NotificationType type = switch (event.reason()) {
            case ROLE_CHANGE -> NotificationType.ROLE_CHANGE;
            case ACCOUNT_LOCKED -> NotificationType.ACCOUNT_LOCKED;
            case ACCOUNT_UNLOCKED -> NotificationType.ACCOUNT_UNLOCKED;
        };

        notificationService.createNotification(event.userId(), title, content, type);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePlaylistContentAdded(PlaylistContentAddedEvent event) {
        log.debug("플레이리스트 콘텐츠 추가 알림 처리 시작 - subscriberId: {}", event.subscriberId());
        notificationService.createNotification(
            event.subscriberId(),
            "구독한 플레이리스트에 새 콘텐츠가 추가되었습니다.",
            event.playlistTitle() + "에 새 콘텐츠가 추가되었습니다.",
            NotificationType.PLAYLIST_CONTENT_ADDED
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePlaylistSubscribed(PlaylistSubscribedEvent event) {
        log.debug("플레이리스트 구독 알림 처리 시작 - ownerId: {}", event.ownerId());
        notificationService.createNotification(
            event.ownerId(),
            "플레이리스트에 새 구독자가 생겼습니다.",
            event.subscriberName() + "님이 " + event.playlistTitle() + "을(를) 구독했습니다.",
            NotificationType.PLAYLIST_SUBSCRIBE
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDirectMessageReceived(DirectMessageReceivedEvent event) {
        log.debug("DM 수신 알림 처리 시작 - receiverId: {}", event.receiverId());
        notificationService.createNotification(
            event.receiverId(),
            "새로운 메시지가 도착했습니다.",
            event.senderName() + ": " + event.messageContent(),
            NotificationType.DIRECT_MESSAGE
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFollowCreated(FollowCreatedEvent event) {
        log.debug("신규 팔로워 알림 처리 시작 - followeeId: {}", event.followeeId());
        notificationService.createNotification(
            event.followeeId(),
            "새로운 팔로워가 생겼습니다.",
            event.followerName() + "님이 팔로우했습니다.",
            NotificationType.NEW_FOLLOWER
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePlaylistCreated(PlaylistCreatedEvent event) {
        log.debug("플레이리스트 생성 팔로워 알림 처리 시작 - ownerId: {}", event.ownerId());
        followService.getFollowerIds(event.ownerId())
            .forEach(followerId -> notificationService.createNotification(
                followerId,
                event.ownerName() + "님의 새로운 활동이 있습니다.",
                "새 플레이리스트를 만들었습니다.",
                NotificationType.FOLLOWEE_ACTIVITY
            ));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReviewWritten(ReviewWrittenEvent event) {
        log.debug("리뷰 작성 팔로워 알림 처리 시작 - authorId: {}", event.authorId());
        followService.getFollowerIds(event.authorId())
            .forEach(followerId -> notificationService.createNotification(
                followerId,
                event.authorName() + "님의 새로운 활동이 있습니다.",
                "리뷰를 작성했습니다.",
                NotificationType.FOLLOWEE_ACTIVITY
            ));
    }
}
