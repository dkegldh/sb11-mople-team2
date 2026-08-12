package com.codeit.mople.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.PlaylistSortBy;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.SortDirection;
import com.codeit.mople.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.mople.domain.playlist.dto.response.PlaylistContentResponse;
import com.codeit.mople.domain.playlist.dto.response.PlaylistCursorResponse;
import com.codeit.mople.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.entity.PlaylistContent;
import com.codeit.mople.domain.playlist.entity.PlaylistSubscription;
import com.codeit.mople.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistUnsubscribedEvent;
import com.codeit.mople.domain.playlist.exception.PlaylistErrorCode;
import com.codeit.mople.domain.playlist.exception.PlaylistException;
import com.codeit.mople.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.mople.domain.playlist.repository.PlaylistRepository;
import com.codeit.mople.domain.playlist.repository.PlaylistSubscriptionRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.dto.UserSummary;
import com.codeit.mople.global.error.CustomException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class PlaylistServiceTest {

  @Mock
  private PlaylistRepository playlistRepository;

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private PlaylistContentRepository playlistContentRepository;

  @Mock
  private PlaylistSubscriptionRepository playlistSubscriptionRepository;

  @Mock
  private ApplicationEventPublisher publisher;

  @Captor
  private ArgumentCaptor<PlaylistSubscribedEvent> subscribedEventCaptor;

  @Captor
  private ArgumentCaptor<PlaylistUnsubscribedEvent> unsubscribedEventCaptor;

  @Captor
  private ArgumentCaptor<PlaylistContentAddedEvent> contentAddedEventCaptor;

  @InjectMocks
  private PlaylistService playlistService;

  private User owner;
  private UUID ownerId;
  private String title;
  private String description;
  private PlaylistCreateRequest createRequest;

  private Playlist mockPlaylist;
  private Playlist playlist;
  private Content content;
  private UUID contentId;
  private UUID playlistId;

  private UUID userId;

  private PlaylistUpdateRequest updateRequest;

  @BeforeEach
  void setUp() {
    owner = mock(User.class);
    ownerId = UUID.randomUUID();
    title = "새 플레이리스트 (1)";
    description = "새로운 플레이리스트입니다.";
    createRequest = new PlaylistCreateRequest(title, description);

    mockPlaylist = mock(Playlist.class);
    playlistId = UUID.randomUUID();
    playlist = Playlist.create(owner, title, description);

    updateRequest = new PlaylistUpdateRequest("수정한 제목", "수정한 설명");
  }

  @Nested
  @DisplayName("플레이리스트 생성")
  class Create {

    @Test
    @DisplayName("플레이리스트 생성 성공")
    void create_success() {
      // given

      // setUp()에서 owner, ownerId, title, description, createRequest 초기화

      Playlist playlist = Playlist.create(owner, title, description);

      given(owner.getId())
          .willReturn(ownerId);
      given(owner.getName())
          .willReturn("test");
      given(owner.getProfileImageUrl())
          .willReturn(null);

      UserSummary ownerResponse = new UserSummary(
          ownerId,
          "test",
          null
      );

      // ownerId DB 조회 → playlist DB 저장 → PlaylistOwnerMapper 생성 → PlaylistMapper 생성 순

      given(userRepository.findById(ownerId))
          .willReturn(Optional.of(owner));

      given(playlistRepository.save(any(Playlist.class)))
          .willReturn(playlist);

      PlaylistResponse response = PlaylistResponse.from(
          playlist,
          ownerResponse,
          false,
          List.of()
      );

      // when
      PlaylistResponse result = playlistService.create(createRequest, ownerId);

      // then
      // 결과 중심(상태 검증)
      assertThat(result).isEqualTo(response);

      // 행위 중심(given(...) 메서드가 호출됐는지 검증)
      verify(userRepository).findById(ownerId);
      verify(playlistRepository).save(any(Playlist.class));
      verify(publisher).publishEvent(new PlaylistCreatedEvent(ownerId, "test", title));
    }

    @Test
    @DisplayName("플레이리스트 생성 실패 - 사용자가 존재하지 않음")
    void create_fail_notFoundUser() {
      // given
      UUID notExistOwnerId = UUID.randomUUID();

      // BeforeEach에서 createRequest 초기화

      given(userRepository.findById(notExistOwnerId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
          playlistService.create(createRequest, notExistOwnerId))
          .isInstanceOf(UserException.class)
          .extracting("errorCode")
          .isEqualTo(UserErrorCode.USER_NOT_FOUND);

      // userRepository.findById() 호출
      verify(userRepository).findById(notExistOwnerId);

      // 나머지 PlaylistService.create()의 내부 메서드 미호출
      verifyNoInteractions(playlistRepository);
    }

  }

  @Nested
  @DisplayName("플레이리스트 단건 조회")
  class Find {

    @BeforeEach
    void setUp() {
      userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("플레이리스트 단건 조회 성공")
    void find_success() {
      // given

      // BeforeEach에서 owner, playlist, playlistId를 초기화

      content = mock(Content.class);
      contentId = UUID.randomUUID();

      given(content.getId())
          .willReturn(contentId);
      given(content.getType())
          .willReturn(ContentType.MOVIE);
      given(content.getTitle())
          .willReturn("타이타닉");
      given(content.getDescription())
          .willReturn("설명");
      given(content.getThumbnailUrl())
          .willReturn(null);
      given(content.getTags())
          .willReturn(List.of("로맨스"));
      given(content.getAverageRating())
          .willReturn(0.0);
      given(content.getReviewCount())
          .willReturn(0);

      given(owner.getId())
          .willReturn(ownerId);
      given(owner.getName())
          .willReturn("test");
      given(owner.getProfileImageUrl())
          .willReturn(null);

      UserSummary ownerResponse = new UserSummary(
          ownerId,
          "test",
          null
      );

      PlaylistContent playlistContent = mock(PlaylistContent.class);
      given(playlistContent.getContent())
          .willReturn(content);
      PlaylistContentResponse playlistContentResponse =
          PlaylistContentResponse.from(playlistContent);

      given(playlistRepository.findById(playlistId))
          .willReturn(Optional.of(playlist));

      given(playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId, userId))
          .willReturn(true);

      given(playlistContentRepository.findAllByPlaylistIdOrderByCreatedAtAsc(playlistId))
          .willReturn(List.of(playlistContent));

      PlaylistResponse response = PlaylistResponse.from(
          playlist,
          ownerResponse,
          true,
          List.of(playlistContentResponse)
      );

      // when
      PlaylistResponse result = playlistService.find(playlistId, userId);

      // then
      assertThat(result).isEqualTo(response);

      verify(playlistRepository).findById(playlistId);
      verify(playlistContentRepository).findAllByPlaylistIdOrderByCreatedAtAsc(playlistId);
    }

    @Test
    @DisplayName("플레이리스트 단건 조회 실패 - 플레이리스트가 존재하지 않음")
    void find_fail_notFoundPlaylist() {
      // given
      UUID notExistPlaylistId = UUID.randomUUID();

      given(playlistRepository.findById(notExistPlaylistId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> playlistService.find(notExistPlaylistId, userId))
          .isInstanceOf(PlaylistException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_NOT_FOUND);

      verify(playlistRepository).findById(notExistPlaylistId);

      verifyNoInteractions(
          playlistContentRepository,
          playlistSubscriptionRepository
      );
    }

  }

  @Nested
  @DisplayName("플레이리스트 목록 조회")
  class FindAll {

    @BeforeEach
    void setUp() {
      userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 기본 조건")
    void findAll_success() {
      // given

      // BeforeEach에서 owner, ownerId, title, description, mockPlaylist 초기화

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      Instant updatedAt = Instant.now();

      given(owner.getId())
          .willReturn(ownerId);
      given(owner.getName())
          .willReturn("owner");
      given(owner.getProfileImageUrl())
          .willReturn(null);

      given(mockPlaylist.getId())
          .willReturn(playlistId);
      given(mockPlaylist.getOwner())
          .willReturn(owner);
      given(mockPlaylist.getUpdatedAt())
          .willReturn(updatedAt);
      given(mockPlaylist.getSubscriberCount())
          .willReturn(0L);

      given(playlistRepository.findAll(condition))
          .willReturn(List.of(mockPlaylist));
      given(playlistRepository.count(condition))
          .willReturn(1L);

      given(playlistContentRepository.findAllByPlaylistIdInOrderByCreatedAtAsc(
          List.of(playlistId)
      ))
          .willReturn(List.of());

      given(playlistSubscriptionRepository
          .findPlaylistIdsBySubscriberIdAndPlaylistIdIn(
              userId,
              List.of(playlistId)
          ))
          .willReturn(List.of());

      // when
      PlaylistCursorResponse result = playlistService.findAll(condition, userId);

      // then
      assertThat(result.data()).hasSize(1);
      assertThat(result.hasNext()).isFalse();
      assertThat(result.totalCount()).isEqualTo(1L);
      assertThat(result.nextCursor()).isNull();
      assertThat(result.nextIdAfter()).isNull();

      verify(playlistRepository).findAll(condition);
      verify(playlistRepository).count(condition);
      verify(playlistContentRepository)
          .findAllByPlaylistIdInOrderByCreatedAtAsc(List.of(playlistId));
      verify(playlistSubscriptionRepository)
          .findPlaylistIdsBySubscriberIdAndPlaylistIdIn(userId, List.of(playlistId));
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 다음 페이지 존재")
    void findAll_success_hasNext() {
      // given

      // BeforeEach에서 owner, ownerId, mockPlaylist 초기화

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          2,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      Instant mockUpdatedAt = Instant.now();

      Playlist nextPlaylist = mock(Playlist.class);
      UUID nextPlaylistId = UUID.randomUUID();
      Instant nextUpdatedAt = mockUpdatedAt.plusSeconds(1);

      given(owner.getId())
          .willReturn(ownerId);
      given(owner.getName())
          .willReturn("owner");
      given(owner.getProfileImageUrl())
          .willReturn(null);

      given(mockPlaylist.getId())
          .willReturn(playlistId);
      given(mockPlaylist.getOwner())
          .willReturn(owner);
      given(mockPlaylist.getUpdatedAt())
          .willReturn(mockUpdatedAt);
      given(mockPlaylist.getSubscriberCount())
          .willReturn(0L);

      given(nextPlaylist.getId())
          .willReturn(nextPlaylistId);
      given(nextPlaylist.getOwner())
          .willReturn(owner);
      given(nextPlaylist.getUpdatedAt())
          .willReturn(nextUpdatedAt);
      given(nextPlaylist.getSubscriberCount())
          .willReturn(0L);

      // 임시 Playlist Mock 객체를 추가하여 3개가 들어있는거로 가장
      given(playlistRepository.findAll(condition))
          .willReturn(List.of(mockPlaylist, nextPlaylist, mock(Playlist.class)));
      given(playlistRepository.count(condition))
          .willReturn(3L);

      given(playlistContentRepository
          .findAllByPlaylistIdInOrderByCreatedAtAsc(
              List.of(playlistId, nextPlaylistId)
          ))
          .willReturn(List.of());

      given(playlistSubscriptionRepository
          .findPlaylistIdsBySubscriberIdAndPlaylistIdIn(
              userId,
              List.of(playlistId, nextPlaylistId)
          ))
          .willReturn(List.of());

      // when
      PlaylistCursorResponse result = playlistService.findAll(condition, userId);

      // then
      assertThat(result.data()).hasSize(2);
      assertThat(result.hasNext()).isTrue();
      assertThat(result.totalCount()).isEqualTo(3L);
      assertThat(result.nextCursor()).isEqualTo(nextUpdatedAt.toString());
      assertThat(result.nextIdAfter()).isEqualTo(nextPlaylistId);
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 마지막 페이지")
    void findAll_success_lastPage() {
      // given

      // BeforeEach에서 owner, ownerId, mockPlaylist 초기화

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          2,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      Instant mockUpdatedAt = Instant.now();

      Playlist lastPlaylist = mock(Playlist.class);
      UUID lastPlaylistId = UUID.randomUUID();
      Instant lastUpdatedAt = mockUpdatedAt.plusSeconds(1);

      given(owner.getId())
          .willReturn(ownerId);
      given(owner.getName())
          .willReturn("owner");
      given(owner.getProfileImageUrl())
          .willReturn(null);

      given(mockPlaylist.getId())
          .willReturn(playlistId);
      given(mockPlaylist.getOwner())
          .willReturn(owner);
      given(mockPlaylist.getUpdatedAt())
          .willReturn(Instant.now());
      given(mockPlaylist.getSubscriberCount())
          .willReturn(0L);

      given(lastPlaylist.getId())
          .willReturn(lastPlaylistId);
      given(lastPlaylist.getOwner())
          .willReturn(owner);
      given(lastPlaylist.getUpdatedAt())
          .willReturn(lastUpdatedAt);
      given(lastPlaylist.getSubscriberCount())
          .willReturn(1L);

      given(playlistRepository.findAll(condition))
          .willReturn(List.of(mockPlaylist, lastPlaylist));

      given(playlistRepository.count(condition))
          .willReturn(2L);

      given(playlistContentRepository
          .findAllByPlaylistIdInOrderByCreatedAtAsc(
              List.of(playlistId, lastPlaylistId)
          ))
          .willReturn(List.of());

      given(playlistSubscriptionRepository
          .findPlaylistIdsBySubscriberIdAndPlaylistIdIn(
              userId,
              List.of(playlistId, lastPlaylistId)
          ))
          .willReturn(List.of());

      // when
      PlaylistCursorResponse result = playlistService.findAll(condition, userId);

      // then
      assertThat(result.data()).hasSize(2);
      assertThat(result.hasNext()).isFalse();
      assertThat(result.totalCount()).isEqualTo(2L);
      assertThat(result.nextCursor()).isNull();
      assertThat(result.nextIdAfter()).isNull();
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 내가 구독한 플레이리스트 포함")
    void findAll_success_subscribed() {
      // given

      // BeforeEach에서 owner, ownerId, mockPlaylist 초기화

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      given(owner.getId())
          .willReturn(ownerId);
      given(owner.getName())
          .willReturn("owner");
      given(owner.getProfileImageUrl())
          .willReturn(null);

      given(mockPlaylist.getId())
          .willReturn(playlistId);
      given(mockPlaylist.getOwner())
          .willReturn(owner);

      given(playlistRepository.findAll(condition))
          .willReturn(List.of(mockPlaylist));

      given(playlistRepository.count(condition))
          .willReturn(1L);

      given(playlistContentRepository
          .findAllByPlaylistIdInOrderByCreatedAtAsc(
              List.of(playlistId)
          ))
          .willReturn(List.of());

      given(playlistSubscriptionRepository
          .findPlaylistIdsBySubscriberIdAndPlaylistIdIn(
              userId,
              List.of(playlistId)
          ))
          .willReturn(List.of(playlistId));

      // when
      PlaylistCursorResponse result =
          playlistService.findAll(condition, userId);

      // then
      assertThat(result.data().get(0).subscribedByMe())
          .isTrue();
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 조회 결과 없음")
    void findAll_success_empty() {
      // given

      // BeforeEach에서 owner, ownerId, mockPlaylist 초기화

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      given(playlistRepository.findAll(condition))
          .willReturn(List.of());

      given(playlistRepository.count(condition))
          .willReturn(0L);

      // when
      PlaylistCursorResponse result = playlistService.findAll(condition, userId);

      // then
      assertThat(result.data()).isEmpty();
      assertThat(result.hasNext()).isFalse();
      assertThat(result.totalCount()).isZero();
      assertThat(result.nextCursor()).isNull();
      assertThat(result.nextIdAfter()).isNull();

      // 실패가 아니라 플레이리스트가 리스트에 없는 성공 테스트이기 때문에 호출 검증
      verify(playlistRepository).findAll(condition);
      verify(playlistRepository).count(condition);

      verifyNoInteractions(
          playlistSubscriptionRepository,
          playlistContentRepository
      );
    }

  }

  @Nested
  @DisplayName("플레이리스트 수정")
  class Update {

    @Test
    @DisplayName("플레이리스트 수정 성공")
    void update_success() {
      // given

      // BeforeEach에서 updateRequest 초기화

      given(owner.getId())
          .willReturn(ownerId);
      given(owner.getName())
          .willReturn("test");
      given(owner.getProfileImageUrl())
          .willReturn(null);

      given(playlistRepository.findById(playlistId))
          .willReturn(Optional.of(playlist));

      given(playlistContentRepository.findAllByPlaylistIdOrderByCreatedAtAsc(playlistId))
          .willReturn(List.of());

      // when
      playlistService.update(playlistId, updateRequest, ownerId);

      // then
      assertThat(playlist.getTitle()).isEqualTo("수정한 제목");
      assertThat(playlist.getDescription()).isEqualTo("수정한 설명");

      verify(playlistRepository).findById(playlistId);
      verify(playlistContentRepository).findAllByPlaylistIdOrderByCreatedAtAsc(playlistId);
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 플레이리스트가 존재하지 않음")
    void update_fail_notFoundPlaylist() {
      // given
      given(playlistRepository.findById(playlistId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> playlistService.update(playlistId, updateRequest, ownerId))
          .isInstanceOf(PlaylistException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_NOT_FOUND);

      verify(playlistRepository).findById(playlistId);

      verifyNoInteractions(playlistContentRepository);
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 플레이리스트 소유자가 아님")
    void update_fail_forbidden() {
      // given
      UUID noOwnerId = UUID.randomUUID();

      given(playlistRepository.findById(playlistId))
          .willReturn(Optional.of(mockPlaylist));

      given(mockPlaylist.getOwner())
          .willReturn(owner);

      given(owner.getId())
          .willReturn(ownerId);

      // when & then
      assertThatThrownBy(() -> playlistService.update(playlistId, updateRequest, noOwnerId))
          .isInstanceOf(PlaylistException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_FORBIDDEN);

      verify(playlistRepository).findById(playlistId);

      verifyNoInteractions(playlistContentRepository);
    }
  }

  @Nested
  @DisplayName("플레이리스트 삭제")
  class Delete {

    @Test
    @DisplayName("플레이리스트 삭제 성공")
    void delete_success() {
      // given

      // BeforeEach에서 playlist, playlistId 초기화

      given(owner.getId())
          .willReturn(ownerId);

      given(playlistRepository.findById(playlistId))
          .willReturn(Optional.of(playlist));

      // playlistContentRepository.deleteAllByPlaylist(playlistId)는 void 타입이기 때문에 given X(반환값이 없기 때문)

      // when
      playlistService.delete(playlistId, ownerId);

      // then
      // void 타입이기 때문에 assert 메서드 불필요
      verify(playlistRepository).findById(playlistId);
      verify(playlistContentRepository).deleteAllByPlaylistId(playlistId);
      verify(playlistRepository).delete(playlist);
    }

    @Test
    @DisplayName("플레이리스트 삭제 실패 - 플레이리스트가 존재하지 않음")
    void delete_fail_notFoundPlaylist() {
      // given

      // BeforeEach에서 playlist, playlistId 초기화

      given(playlistRepository.findById(playlistId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
          playlistService.delete(playlistId, ownerId)
      )
          .isInstanceOf(PlaylistException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_NOT_FOUND);

      verify(playlistRepository).findById(playlistId);

      // playlistContentRepository.deleteAllByPlaylistId() 메서드는 호출되지 않음
      verify(playlistContentRepository, never())
          .deleteAllByPlaylistId(playlistId);

      // playlistRepository.delete() 메서드는 호출되지 않음
      verify(playlistRepository, never())
          .delete(any(Playlist.class));
    }

    @Test
    @DisplayName("플레이리스트 삭제 실패 - 플레이리스트 소유자가 아님")
    void delete_fail_forbidden() {
      // given
      UUID noOwnerId = UUID.randomUUID();

      // BeforeEach에서 playlist, playlistId 초기화

      given(mockPlaylist.getOwner())
          .willReturn(owner);

      given(owner.getId())
          .willReturn(ownerId);

      given(playlistRepository.findById(playlistId))
          .willReturn(Optional.of(mockPlaylist));

      // when & then
      assertThatThrownBy(() ->
          playlistService.delete(playlistId, noOwnerId)
      )
          .isInstanceOf(PlaylistException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_FORBIDDEN);

      verify(playlistRepository).findById(playlistId);

      verify(playlistContentRepository, never())
          .deleteAllByPlaylistId(playlistId);

      verify(playlistRepository, never())
          .delete(any(Playlist.class));
    }
  }

  @Nested
  @DisplayName("플레이리스트 구독")
  class Subscribe {

    @Test
    @DisplayName("플레이리스트 구독 성공")
    void subscribe_success() {
      // given
      UUID playlistId = UUID.randomUUID();
      UUID subscriberId = UUID.randomUUID();

      Playlist playlist = Playlist.create(owner, title, description);
      User subscriber = mock(User.class);
      given(subscriber.getName()).willReturn("구독자");

      given(playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId,
          subscriberId)).willReturn(false);
      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(userRepository.findById(subscriberId)).willReturn(Optional.of(subscriber));
      given(playlistSubscriptionRepository.save(any(PlaylistSubscription.class))).willReturn(
          PlaylistSubscription.create(playlist, subscriber));

      // when
      playlistService.subscribe(playlistId, subscriberId);

      // then
      verify(playlistSubscriptionRepository).save(any(PlaylistSubscription.class));
      verify(publisher).publishEvent(subscribedEventCaptor.capture());

      PlaylistSubscribedEvent event = subscribedEventCaptor.getValue();

      assertThat(event.playlistId()).isEqualTo(playlistId);
      assertThat(event.subscriberId()).isEqualTo(subscriberId);
      assertThat(event.subscriberName()).isEqualTo("구독자");
      assertThat(event.playlistTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("이미 구독한 플레이리스트면 예외")
    void subscribe_duplicate() {
      UUID playlistId = UUID.randomUUID();
      UUID subscriberId = UUID.randomUUID();
      Playlist playlist = Playlist.create(owner, title, description);
      User subscriber = mock(User.class);

      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(userRepository.findById(subscriberId)).willReturn(Optional.of(subscriber));
      given(playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId,
          subscriberId)).willReturn(true);

      assertThatThrownBy(() -> playlistService.subscribe(playlistId, subscriberId))
          .isInstanceOf(CustomException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.SUBSCRIBE_DUPLICATE);

      verify(playlistSubscriptionRepository, never()).save(any());
      verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("플래이리스트가 없으면 예외")
    void subscribe_playlistNotFound() {
      UUID playlistId = UUID.randomUUID();
      UUID subscriberId = UUID.randomUUID();

      given(playlistRepository.findById(playlistId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> playlistService.subscribe(playlistId, subscriberId))
          .isInstanceOf(CustomException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.SUBSCRIBE_NOT_FOUND);

      verify(playlistSubscriptionRepository, never()).save(any());
      verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("구독자가 없으면 예외")
    void subscribe_userNotFound() {
      UUID playlistId = UUID.randomUUID();
      UUID subscriberId = UUID.randomUUID();
      Playlist playlist = Playlist.create(owner, title, description);

      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(userRepository.findById(subscriberId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> playlistService.subscribe(playlistId, subscriberId))
          .isInstanceOf(CustomException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.SUBSCRIBE_USER_NOT_FOUND);

      verify(playlistSubscriptionRepository, never()).save(any());
      verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("본인 플레이리스트 구독 차단")
    void subscribe_selfSubScribe() {
      UUID playlistId = UUID.randomUUID();
      Playlist playlist = Playlist.create(owner, title, description);

      given(owner.getId()).willReturn(ownerId);
      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));

      assertThatThrownBy(() -> playlistService.subscribe(playlistId, ownerId))
          .isInstanceOf(CustomException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.SUBSCRIBE_NOT_ALLOWED);

      verify(userRepository, never()).findById(any());
      verify(playlistSubscriptionRepository, never()).save(any());
      verify(publisher, never()).publishEvent(any());
    }

  }

  @Nested
  @DisplayName("플레이리스트 구독 취소")
  class UnSubscribe {

    @Test
    @DisplayName("플레이리스트 구독 취소 성공")
    void unSubscribe_success() {
      UUID playlistId = UUID.randomUUID();
      UUID subscriberId = UUID.randomUUID();

      given(playlistSubscriptionRepository.deleteByPlaylistIdAndSubscriberId(playlistId,
          subscriberId)).willReturn(1);

      // when
      playlistService.unSubscribe(playlistId, subscriberId);

      // then
      verify(playlistSubscriptionRepository).deleteByPlaylistIdAndSubscriberId(playlistId,
          subscriberId);
      verify(publisher).publishEvent(unsubscribedEventCaptor.capture());

      PlaylistUnsubscribedEvent event = unsubscribedEventCaptor.getValue();

      assertThat(event.playlistId()).isEqualTo(playlistId);
      assertThat(event.subscriberId()).isEqualTo(subscriberId);
    }

    @Test
    @DisplayName("구독하지 않은 플레이리스트면 예외")
    void unSubscribe_notFound() {
      UUID playlistId = UUID.randomUUID();
      UUID subscriberId = UUID.randomUUID();

      given(playlistSubscriptionRepository.deleteByPlaylistIdAndSubscriberId(playlistId,
          subscriberId)).willReturn(0);

      assertThatThrownBy(() -> playlistService.unSubscribe(playlistId, subscriberId))
          .isInstanceOf(PlaylistException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.UNSUBSCRIBE_NOT_FOUND);

      verify(playlistRepository, never()).decreaseSubscriberCount(any());
    }
  }

  @Nested
  @DisplayName("플레이리스트 콘텐츠 생성")
  class addContent {

    @Test
    @DisplayName("플레이리스트 콘텐츠 추가 성공")
    void addContent_success() {
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();

      Playlist playlist = Playlist.create(owner, title, description);
      Content content = mock(Content.class);

      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
      given(owner.getId()).willReturn(ownerId);
      given(playlistSubscriptionRepository.findSubscriberIdsByPlaylistId(playlistId))
          .willReturn(List.of(UUID.randomUUID()));

      // when
      playlistService.addContent(playlistId, contentId, ownerId);

      // then
      verify(playlistContentRepository).save(any(PlaylistContent.class));
      verify(publisher).publishEvent(contentAddedEventCaptor.capture());

      PlaylistContentAddedEvent event = contentAddedEventCaptor.getValue();

      assertThat(event.playlistId()).isEqualTo(playlistId);
      assertThat(event.contentId()).isEqualTo(contentId);
      assertThat(event.playlistTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("플레이리스트 콘텐츠 추가 시 구독자 수만큼 이벤트가 발행된다")
    void addContent_publishes_event_per_subscriber() {
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();
      UUID subscriber1 = UUID.randomUUID();
      UUID subscriber2 = UUID.randomUUID();

      Playlist playlist = Playlist.create(owner, title, description);
      Content content = mock(Content.class);

      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
      given(owner.getId()).willReturn(ownerId);
      given(playlistSubscriptionRepository.findSubscriberIdsByPlaylistId(playlistId))
          .willReturn(List.of(subscriber1, subscriber2));

      // when
      playlistService.addContent(playlistId, contentId, ownerId);

      // then
      verify(publisher, times(2)).publishEvent(contentAddedEventCaptor.capture());

      List<PlaylistContentAddedEvent> events = contentAddedEventCaptor.getAllValues();
      assertThat(events).extracting(PlaylistContentAddedEvent::subscriberId)
          .containsExactlyInAnyOrder(subscriber1, subscriber2);
      assertThat(events).allSatisfy(event -> {
        assertThat(event.playlistId()).isEqualTo(playlistId);
        assertThat(event.contentId()).isEqualTo(contentId);
        assertThat(event.playlistTitle()).isEqualTo(title);
      });
    }

    @Test
    @DisplayName("플레이리스트가 없으면 예외")
    void addContent_playlistNotFound() {
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();
      given(playlistRepository.findById(playlistId)).willReturn(Optional.empty());

      // when then
      assertThatThrownBy(() -> playlistService.addContent(playlistId, contentId, ownerId))
          .isInstanceOf(PlaylistException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.PLAYLIST_CONTENT_PLAY_NOT_FOUND);

      verify(publisher, never()).publishEvent(any());
      verify(playlistContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("콘텐츠가 없으면 예외")
    void addContent_contentNotFound() {
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();
      Playlist playlist = Playlist.create(owner, title, description);
      given(owner.getId()).willReturn(ownerId);
      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(contentRepository.findById(contentId)).willReturn(Optional.empty());

      // when then
      assertThatThrownBy(() -> playlistService.addContent(playlistId, contentId, ownerId))
          .isInstanceOf(PlaylistException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.PLAYLIST_CONTENT_CONTENT_NOT_FOUND);

      verify(publisher, never()).publishEvent(any());
      verify(playlistContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 있는 콘텐츠면 예외")
    void addContent_duplicate() {
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();
      Playlist playlist = Playlist.create(owner, title, description);
      Content content = mock(Content.class);

      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
      given(owner.getId()).willReturn(ownerId);
      given(playlistContentRepository.existsByPlaylistIdAndContentId(playlistId,
          contentId)).willReturn(true);

      // when then
      assertThatThrownBy(() -> playlistService.addContent(playlistId, contentId, ownerId))
          .isInstanceOf(PlaylistException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.PLAYLIST_CONTENT_DUPLICATE);

      verify(publisher, never()).publishEvent(any());
      verify(playlistContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("플레이리스트 소유자가 아니면 예외")
    void addContent_forbidden() {
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();
      UUID noOwnerId = UUID.randomUUID();
      Playlist playlist = mock(Playlist.class);

      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(playlist.getOwner()).willReturn(owner);
      given(owner.getId()).willReturn(ownerId);

      // when then
      assertThatThrownBy(() -> playlistService.addContent(playlistId, contentId, noOwnerId))
          .isInstanceOf(PlaylistException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.PLAYLIST_FORBIDDEN);

      verify(publisher, never()).publishEvent(any());
      verify(playlistContentRepository, never()).existsByPlaylistIdAndContentId(any(), any());
      verify(playlistContentRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("플레이리스트 콘텐츠 삭제")
  class removeContent {

    @Test
    @DisplayName("플레이리스트 콘텐츠 삭제 성공")
    void removeContent_success() {
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();
      Playlist playlist = Playlist.create(owner, title, description);
      Content content = mock(Content.class);
      PlaylistContent playlistContent = PlaylistContent.create(playlist, content);

      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(owner.getId()).willReturn(ownerId);
      given(playlistContentRepository.findByPlaylistIdAndContentId(playlistId, contentId))
          .willReturn(Optional.of(playlistContent));

      // when
      playlistService.removeContent(playlistId, contentId, ownerId);

      // then
      verify(publisher, never()).publishEvent(any());
      verify(playlistContentRepository).delete(playlistContent);
    }

    @Test
    @DisplayName("플레이리스트가 없으면 예외")
    void removeContent_playlistNotFound() {
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();

      given(playlistRepository.findById(playlistId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> playlistService.removeContent(playlistId, contentId, ownerId))
          .isInstanceOf(PlaylistException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.PLAYLIST_CONTENT_PLAY_NOT_FOUND);

      verify(playlistContentRepository, never()).findByPlaylistIdAndContentId(any(), any());
      verify(playlistContentRepository, never()).delete(any());

    }

    @Test
    @DisplayName("플레이리스트 소유자가 아니면 예외")
    void removeContent_forbidden() {
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();
      UUID noOwnerId = UUID.randomUUID();
      Playlist playlist = mock(Playlist.class);

      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(playlist.getOwner()).willReturn(owner);
      given(owner.getId()).willReturn(ownerId);

      assertThatThrownBy(() -> playlistService.removeContent(playlistId, contentId, noOwnerId))
          .isInstanceOf(PlaylistException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.PLAYLIST_FORBIDDEN);

      verify(playlistContentRepository, never()).findByPlaylistIdAndContentId(any(), any());
      verify(playlistContentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("플레이리스트에 없는 콘텐츠면 예외")
    void removeContent_notInPlaylist() {
      UUID playlistId = UUID.randomUUID();
      UUID contentId = UUID.randomUUID();
      Playlist playlist = Playlist.create(owner, title, description);

      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(owner.getId()).willReturn(ownerId);
      given(playlistContentRepository.findByPlaylistIdAndContentId(playlistId, contentId))
          .willReturn(Optional.empty());

      assertThatThrownBy(() -> playlistService.removeContent(playlistId, contentId, ownerId))
          .isInstanceOf(PlaylistException.class)
          .hasFieldOrPropertyWithValue("errorCode", PlaylistErrorCode.UN_PLAYLIST_CONTENT_NOT_FOUND);

      verify(playlistContentRepository, never()).delete(any());
    }
  }

}
