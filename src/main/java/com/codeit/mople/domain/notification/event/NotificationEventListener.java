package com.codeit.mople.domain.notification.event;

import com.codeit.mople.domain.directmessage.event.DirectMessageReceivedEvent;
import com.codeit.mople.domain.follow.event.FollowCreatedEvent;
import com.codeit.mople.domain.follow.service.FollowService;
import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.service.NotificationCreator;
import com.codeit.mople.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.mople.domain.playlist.service.PlaylistService;
import com.codeit.mople.domain.review.event.ReviewWrittenEvent;
import com.codeit.mople.global.event.UserAccountStatusChangedEvent;
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

  private final NotificationCreator notificationCreator;
  private final FollowService followService;
  private final PlaylistService playlistService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleUserAccountStatusChanged(UserAccountStatusChangedEvent event) {
    log.debug("계정 상태 변경 알림 처리 시작 - userId: {}, reason: {}", event.userId(), event.reason());

    ForceLogoutNotification notification = switch (event.reason()) {
      case ROLE_CHANGE -> new ForceLogoutNotification(
          "권한이 변경되었습니다.",
          "관리자에 의해 권한이 변경되었습니다. 다시 로그인해주세요.",
          NotificationType.ROLE_CHANGE);
      case ACCOUNT_LOCKED -> new ForceLogoutNotification(
          "계정이 잠금되었습니다.",
          "관리자에 의해 계정이 잠금되었습니다.",
          NotificationType.ACCOUNT_LOCKED);
      case ACCOUNT_UNLOCKED -> new ForceLogoutNotification(
          "계정 잠금이 해제되었습니다.",
          "관리자에 의해 계정 잠금이 해제되었습니다.",
          NotificationType.ACCOUNT_UNLOCKED);
    };

    notificationCreator.createNotification(
        event.userId(), notification.title(), notification.content(), notification.type());
  }

  // reason별 title/content/type을 한 switch에서 같이 채워서, reason이 추가될 때 고칠 곳을
  // 하나로 유지한다 (ForceLogoutReason -> NotificationType 매핑 반복 제거 포함).
  private record ForceLogoutNotification(String title, String content, NotificationType type) {

  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handlePlaylistContentAdded(PlaylistContentAddedEvent event) {
    log.debug("플레이리스트 콘텐츠 추가 알림 처리 시작 - playlistId: {}", event.playlistId());
    playlistService.getSubscriberIds(event.playlistId())
        .forEach(subscriberId -> notificationCreator.createNotification(
            subscriberId,
            "구독한 플레이리스트에 새 콘텐츠가 추가되었습니다.",
            event.playlistTitle() + "에 새 콘텐츠가 추가되었습니다.",
            NotificationType.PLAYLIST_CONTENT_ADDED
        ));
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handlePlaylistSubscribed(PlaylistSubscribedEvent event) {
    log.debug("플레이리스트 구독 알림 처리 시작 - ownerId: {}", event.ownerId());
    notificationCreator.createNotification(
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
    notificationCreator.createNotification(
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
    notificationCreator.createNotification(
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
        .forEach(followerId -> notificationCreator.createNotification(
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
        .forEach(followerId -> notificationCreator.createNotification(
            followerId,
            event.authorName() + "님의 새로운 활동이 있습니다.",
            "리뷰를 작성했습니다.",
            NotificationType.FOLLOWEE_ACTIVITY
        ));
  }
}
