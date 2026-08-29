package com.codeit.mople.domain.notification.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
import com.codeit.mople.global.event.ForceLogoutReason;
import com.codeit.mople.global.event.UserAccountStatusChangedEvent;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener 단위 테스트")
class NotificationEventListenerTest {

  @Mock
  private NotificationCreator notificationCreator;

  @Mock
  private FollowService followService;

  @Mock
  private PlaylistService playlistService;

  @InjectMocks
  private NotificationEventListener notificationEventListener;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @Test
  @DisplayName("권한 변경 이벤트 발생 시 ROLE_CHANGE 알림을 생성한다")
  void handle_role_change() {
    // given
    UserAccountStatusChangedEvent event = new UserAccountStatusChangedEvent(
        userId,
        ForceLogoutReason.ROLE_CHANGE,
        true
    );

    // when
    notificationEventListener.handleUserAccountStatusChanged(event);

    // then
    verify(notificationCreator).createNotification(
        userId,
        "권한이 변경되었습니다.",
        "관리자에 의해 권한이 변경되었습니다. 다시 로그인해주세요.",
        NotificationType.ROLE_CHANGE
    );

    verifyNoMoreInteractions(notificationCreator);
  }

  @Test
  @DisplayName("계정 잠금 이벤트 발생 시 ACCOUNT_LOCKED 알림을 생성한다")
  void handle_account_locked() {
    // given
    UserAccountStatusChangedEvent event = new UserAccountStatusChangedEvent(
        userId,
        ForceLogoutReason.ACCOUNT_LOCKED,
        true
    );

    // when
    notificationEventListener.handleUserAccountStatusChanged(event);

    // then
    verify(notificationCreator).createNotification(
        userId,
        "계정이 잠금되었습니다.",
        "관리자에 의해 계정이 잠금되었습니다.",
        NotificationType.ACCOUNT_LOCKED
    );

    verifyNoMoreInteractions(notificationCreator);
  }

  @Test
  @DisplayName("계정 잠금 해제 이벤트 발생 시 ACCOUNT_UNLOCKED 알림을 생성한다")
  void handle_account_unlocked() {
    // given
    UserAccountStatusChangedEvent event = new UserAccountStatusChangedEvent(
        userId,
        ForceLogoutReason.ACCOUNT_UNLOCKED,
        false
    );

    // when
    notificationEventListener.handleUserAccountStatusChanged(event);

    // then
    verify(notificationCreator).createNotification(
        userId,
        "계정 잠금이 해제되었습니다.",
        "관리자에 의해 계정 잠금이 해제되었습니다.",
        NotificationType.ACCOUNT_UNLOCKED
    );

    verifyNoMoreInteractions(notificationCreator);
  }

  @Test
  @DisplayName("플레이리스트 구독 이벤트 발생 시 owner에게 PLAYLIST_SUBSCRIBE 알림을 생성한다")
  void handle_playlist_subscribed() {
    // given
    UUID eventId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();

    PlaylistSubscribedEvent event = new PlaylistSubscribedEvent(eventId, Instant.now(),
        ownerId,
        playlistId,
        subscriberId,
        "구독자유저",
        "테스트 플레이리스트"
    );

    // when
    notificationEventListener.handlePlaylistSubscribed(event);

    // then
    verify(notificationCreator).createNotification(
        ownerId,
        "플레이리스트에 새 구독자가 생겼습니다.",
        "구독자유저님이 테스트 플레이리스트을(를) 구독했습니다.",
        NotificationType.PLAYLIST_SUBSCRIBE
    );

    verifyNoMoreInteractions(notificationCreator);
  }

  @Test
  @DisplayName("DM 수신 이벤트 발생 시 수신자에게 DIRECT_MESSAGE 알림을 생성한다")
  void handle_direct_message_received() {
    // given
    UUID receiverId = UUID.randomUUID();

    DirectMessageReceivedEvent event = new DirectMessageReceivedEvent(
        receiverId,
        "발신자",
        "안녕하세요!"
    );

    // when
    notificationEventListener.handleDirectMessageReceived(event);

    // then
    verify(notificationCreator).createNotification(
        receiverId,
        "새로운 메시지가 도착했습니다.",
        "발신자: 안녕하세요!",
        NotificationType.DIRECT_MESSAGE
    );

    verifyNoMoreInteractions(notificationCreator);
  }

  @Test
  @DisplayName("플레이리스트 생성 이벤트 발생 시 모든 팔로워에게 FOLLOWEE_ACTIVITY 알림을 생성한다")
  void handle_playlist_created() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID followerA = UUID.randomUUID();
    UUID followerB = UUID.randomUUID();

    PlaylistCreatedEvent event = new PlaylistCreatedEvent(
        ownerId,
        "플레이리스트_생성자",
        "새 플레이리스트"
    );

    when(followService.getFollowerIds(ownerId))
        .thenReturn(List.of(followerA, followerB));

    // when
    notificationEventListener.handlePlaylistCreated(event);

    // then
    verify(followService).getFollowerIds(ownerId);

    verify(notificationCreator).createNotification(
        followerA,
        "플레이리스트_생성자님의 새로운 활동이 있습니다.",
        "새 플레이리스트를 만들었습니다.",
        NotificationType.FOLLOWEE_ACTIVITY
    );

    verify(notificationCreator).createNotification(
        followerB,
        "플레이리스트_생성자님의 새로운 활동이 있습니다.",
        "새 플레이리스트를 만들었습니다.",
        NotificationType.FOLLOWEE_ACTIVITY
    );

    verifyNoMoreInteractions(notificationCreator);
  }

  @Test
  @DisplayName("리뷰 작성 이벤트 발생 시 모든 팔로워에게 FOLLOWEE_ACTIVITY 알림을 생성한다")
  void handle_review_written() {
    // given
    UUID authorId = UUID.randomUUID();
    UUID followerA = UUID.randomUUID();
    UUID followerB = UUID.randomUUID();

    ReviewWrittenEvent event = new ReviewWrittenEvent(
        authorId,
        "리뷰작성자"
    );

    when(followService.getFollowerIds(authorId))
        .thenReturn(List.of(followerA, followerB));

    // when
    notificationEventListener.handleReviewWritten(event);

    // then
    verify(followService).getFollowerIds(authorId);

    verify(notificationCreator).createNotification(
        followerA,
        "리뷰작성자님의 새로운 활동이 있습니다.",
        "리뷰를 작성했습니다.",
        NotificationType.FOLLOWEE_ACTIVITY
    );

    verify(notificationCreator).createNotification(
        followerB,
        "리뷰작성자님의 새로운 활동이 있습니다.",
        "리뷰를 작성했습니다.",
        NotificationType.FOLLOWEE_ACTIVITY
    );

    verifyNoMoreInteractions(notificationCreator);
  }

  @Test
  @DisplayName("팔로워가 없으면 플레이리스트 생성 알림을 생성하지 않는다")
  void handle_playlist_created_without_followers() {
    // given
    UUID ownerId = UUID.randomUUID();

    PlaylistCreatedEvent event = new PlaylistCreatedEvent(
        ownerId,
        "플레이리스트_생성자",
        "새 플레이리스트"
    );

    when(followService.getFollowerIds(ownerId))
        .thenReturn(List.of());

    // when
    notificationEventListener.handlePlaylistCreated(event);

    // then
    verify(followService).getFollowerIds(ownerId);

    verifyNoInteractions(notificationCreator);
  }

}