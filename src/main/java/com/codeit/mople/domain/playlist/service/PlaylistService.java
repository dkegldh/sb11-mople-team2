package com.codeit.mople.domain.playlist.service;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.PlaylistSortBy;
import com.codeit.mople.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.mople.domain.playlist.dto.response.PlaylistContentResponse;
import com.codeit.mople.domain.playlist.dto.response.PlaylistCursorResponse;
import com.codeit.mople.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.entity.PlaylistContent;
import com.codeit.mople.domain.playlist.entity.PlaylistSubscription;
import com.codeit.mople.domain.playlist.event.PlaylistContentAddedEvent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistService {

  private final PlaylistRepository playlistRepository;
  private final UserRepository userRepository;
  private final PlaylistContentRepository playlistContentRepository;
  private final ContentRepository contentRepository;
  private final PlaylistSubscriptionRepository playlistSubscriptionRepository;

  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public PlaylistResponse create(PlaylistCreateRequest request, UUID ownerId) {

    log.debug("플레이리스트 생성 시도: ownerId={}, title={}",
        ownerId, request.title());

    User owner = userRepository.findById(ownerId).orElseThrow(() ->
        new UserException(
            UserErrorCode.USER_NOT_FOUND,
            Map.of("userId", ownerId)
        )
    );

    Playlist playlist = Playlist.create(owner, request.title(), request.description());

    Playlist savedPlaylist = playlistRepository.save(playlist);

    UserSummary ownerResponse = toUserSummary(owner);

    PlaylistResponse response = PlaylistResponse.from(
        savedPlaylist,
        ownerResponse,
        false,
        List.of());

    log.info("플레이리스트 생성 완료: playlistId={}, ownerId={}",
        savedPlaylist.getId(), owner.getId());

    return response;
  }

  // 플레이리스트 세부 조회(단건 조회)
  @Transactional(readOnly = true)
  public PlaylistResponse find(UUID playlistId, UUID requesterId) {

    log.debug("플레이리스트 조회 시도: playlistId={}, requesterId={}",
        playlistId, requesterId);

    Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() ->
        new PlaylistException(
            PlaylistErrorCode.PLAYLIST_NOT_FOUND,
            Map.of("playlistId", playlistId))
    );

    UserSummary ownerResponse = toUserSummary(playlist.getOwner());

    boolean subscribedByMe =
        playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId, requesterId);

    // 콘텐츠를 플레이리스트에 추가한 순서대로 표시(콘텐츠를 B, E, A, C 순으로 추가했을 경우 추가한 순서 그대로)
    List<PlaylistContentResponse> contents =
        playlistContentRepository.findAllByPlaylistIdOrderByCreatedAtAsc(playlistId).stream()
            .map(PlaylistContentResponse::from)
            .toList();

    PlaylistResponse response = PlaylistResponse.from(
        playlist,
        ownerResponse,
        subscribedByMe,
        contents
    );

    log.info("플레이리스트 조회 완료: playlistId={}, contentCount={}",
        playlistId, contents.size());

    return response;
  }

  // 플레이리스트 목록 조회
  @Transactional(readOnly = true)
  public PlaylistCursorResponse findAll(PlaylistQueryCondition condition, UUID requesterId) {

    log.debug("플레이리스트 목록 조회 시도: requesterId={}, keywordLike={}, ownerId={}, subscriberId={},"
            + " cursor={}, idAfter={}, limit={}, sortBy={}, sortDirection={}",
        requesterId,
        condition.keywordLike(),
        condition.ownerIdEqual(),
        condition.subscriberIdEqual(),
        condition.cursor(),
        condition.idAfter(),
        condition.limit(),
        condition.sortBy(),
        condition.sortDirection()
    );

    // 목록 조회(limit + 1까지)
    List<Playlist> playlists = playlistRepository.findAll(condition);

    // 다음 페이지가 있는지 확인
    boolean hasNext = playlists.size() > condition.limit();

    // 다음 페이지가 있으면 limit까지만 조회
    if (hasNext) {
      playlists = playlists.subList(0, condition.limit());
    }

    // 목록 조회된 Playlist의 총 개수
    long totalCount = playlistRepository.count(condition);

    // 조회된 플레이리스트 ID
    List<UUID> playlistIds = playlists.stream().map(Playlist::getId).toList();

    // 플레이리스트 별 콘텐츠 조회
    Map<UUID, List<PlaylistContentResponse>> contentsByPlaylistId =
        playlistIds.isEmpty()
            ? Map.of()
            : playlistContentRepository.findAllByPlaylistIdInOrderByCreatedAtAsc(playlistIds)
                .stream()
                .collect(Collectors.groupingBy(pc ->
                        pc.getPlaylist().getId(),
                    Collectors.mapping(
                        PlaylistContentResponse::from,
                        Collectors.toList()
                    )
                ));

    // 현재 사용자가 구독한 플레이리스트 ID들을 조회
    Set<UUID> subscribedPlaylistIds = playlistIds.isEmpty() ?
        Set.of() :
        new HashSet<>(
            playlistSubscriptionRepository
                .findPlaylistIdsBySubscriberIdAndPlaylistIdIn(requesterId, playlistIds)
        );

    List<PlaylistResponse> data = playlists.stream()
        .map(playlist -> PlaylistResponse.from(
            playlist,
            toUserSummary(playlist.getOwner()),
            subscribedPlaylistIds.contains(playlist.getId()), // 구독한 플레이리스트 ID가 포함될 경우 true
            contentsByPlaylistId.getOrDefault(playlist.getId(), List.of()) // 없으면 빈 List를 반환
        ))
        .toList();

    // 임시 커서, 보조 커서 초기화
    String nextCursor = null;
    UUID nextIdAfter = null;

    // 마지막 원소 조회
    if (hasNext && !playlists.isEmpty()) {
      Playlist lastItem = playlists.get(playlists.size() - 1);

      // 보조커서
      nextIdAfter = lastItem.getId();

      // 정렬조건
      if (condition.sortBy() == PlaylistSortBy.UPDATED_AT) {
        nextCursor = lastItem.getUpdatedAt().toString();
      } else if (condition.sortBy() == PlaylistSortBy.SUBSCRIBE_COUNT) {
        nextCursor = String.valueOf(lastItem.getSubscriberCount());
      }
    }

    log.info("플레이리스트 목록 조회 완료: size={}, totalCount={}, hasNext={}",
        data.size(), totalCount, hasNext);

    return new PlaylistCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        condition.sortBy(),
        condition.sortDirection()
    );
  }

  @Transactional
  public PlaylistResponse update(
      UUID playlistId,
      PlaylistUpdateRequest request,
      UUID ownerId
  ) {

    log.debug("플레이리스트 수정 시도: playlistId={}, ownerId={}",
        playlistId, ownerId);

    // Playlist 조회
    Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() ->
        new PlaylistException(
            PlaylistErrorCode.PLAYLIST_NOT_FOUND,
            Map.of("playlistId", playlistId))
    );

    // 플레이리스트 소유자가 맞는지 검증
    validateOwner(playlist, ownerId);

    playlist.update(request.title(), request.description());

    UserSummary ownerResponse = toUserSummary(playlist.getOwner());

    List<PlaylistContentResponse> contents =
        playlistContentRepository.findAllByPlaylistIdOrderByCreatedAtAsc(playlistId).stream()
            .map(PlaylistContentResponse::from)
            .toList();

    PlaylistResponse response = PlaylistResponse.from(
        playlist,
        ownerResponse,
        false, // 컨트롤러에서 403 권한 부족(타인 플레이리스트 수정 금지) + 자기 자신의 플레이리스트는 구독할 수 없음
        contents
    );

    log.info("플레이리스트 수정 완료: playlistId={}, ownerId={}",
        playlistId, ownerId);

    return response;
  }

  @Transactional
  public void delete(UUID playlistId, UUID ownerId) {

    log.debug("플레이리스트 삭제 시도: playlistId={}, ownerId={}",
        playlistId, ownerId);

    Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() ->
        new PlaylistException(
            PlaylistErrorCode.PLAYLIST_NOT_FOUND,
            Map.of("playlistId", playlistId))
    );

    validateOwner(playlist, ownerId);

    // 플레이리리스트 삭제 전 플레이리스트 안에 들어있던 컨텐츠들을 삭제
    playlistContentRepository.deleteAllByPlaylistId(playlistId);

    // deleteById도 가능하지만 where id=로 조회 후 delete하기 때문에(불필요한 조회가 발생함) 조회 실행을 뺌
    playlistRepository.delete(playlist);

    log.info("플레이리스트 삭제 완료: playlistId={}, ownerId={}",
        playlistId, ownerId);

  }

  @Transactional
  public void subscribe(UUID playlistId, UUID subscriberId) {

    log.debug("플레이리스트 구독 시도: playlistId={}, subscriberId={}",
        playlistId, subscriberId);

    // 플레이리스트 존재확인
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.SUBSCRIBE_NOT_FOUND,
            Map.of("playlistId", playlistId)));

    UUID ownerId = playlist.getOwner().getId();

    // 해당 플레이리스트 주인이 본인이면 구독 차단
    if (subscriberId.equals(ownerId)) {
      throw new PlaylistException(PlaylistErrorCode.SUBSCRIBE_NOT_ALLOWED,
          Map.of("subscriberId", subscriberId, "ownerId", ownerId));
    }

    // 플레이리스트를 구독하려는 유저가 존재하는지
    User subscriber = userRepository.findById(subscriberId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.SUBSCRIBE_USER_NOT_FOUND,
            Map.of("subscriberId", subscriberId)));

    // 중복 구독 차단
    if (playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId,
        subscriberId)) {
      throw new PlaylistException(PlaylistErrorCode.SUBSCRIBE_DUPLICATE,
          Map.of("playlistId", playlistId, "subscriberId", subscriberId));
    }

    PlaylistSubscription saved = playlistSubscriptionRepository.save(
        PlaylistSubscription.create(playlist, subscriber));

    eventPublisher.publishEvent(new PlaylistSubscribedEvent(ownerId, playlistId, subscriberId));

    log.info("플레이리스트 구독 성공: playlistSubscriptionId={}, playlistId={}, subscriberId={}",
        saved.getId(), playlistId, subscriberId);
  }

  @Transactional
  public void unSubscribe(UUID playlistId, UUID subscriberId) {
    log.debug("플레이리스트 구독 취소 시도: playlistId={}, subscriberId={}",
        playlistId, subscriberId);

    // 구독 존재 검증 + 삭제
    int deleted = playlistSubscriptionRepository.deleteByPlaylistIdAndSubscriberId(playlistId,
        subscriberId);
    if (deleted == 0) {
      throw new PlaylistException(PlaylistErrorCode.UNSUBSCRIBE_NOT_FOUND,
          Map.of("playlistId", playlistId, "subscriberId", subscriberId));
    }

    eventPublisher.publishEvent(new PlaylistUnsubscribedEvent(playlistId, subscriberId));

    log.info("플레이리스트 구독 취소 성공: playlist={}, subscriberId={}",
        playlistId, subscriberId);
  }

  @Transactional
  public void addContent(UUID playlistId, UUID contentId, UUID requesterId) {
    log.debug("플레이리스트에 콘텐츠 추가 시도: playlistId={}, contentId={}, requesterId={}",
        playlistId, contentId, requesterId);

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PLAYLIST_CONTENT_PLAY_NOT_FOUND,
            Map.of("playlistId", playlistId)));

    // 소유자 검증
    validateOwner(playlist, requesterId);

    Content content = contentRepository.findById(contentId)
        .orElseThrow(
            () -> new PlaylistException(PlaylistErrorCode.PLAYLIST_CONTENT_CONTENT_NOT_FOUND,
                Map.of("contentId", contentId)));

    // 중복 검증
    if (playlistContentRepository.existsByPlaylistIdAndContentId(playlistId, contentId)) {
      throw new PlaylistException(PlaylistErrorCode.PLAYLIST_CONTENT_DUPLICATE,
          Map.of("playlistId", playlistId, "contentId", contentId));
    }

    PlaylistContent playlistContent = PlaylistContent.create(playlist, content);
    playlistContentRepository.save(playlistContent);

    log.info("플레이리스트에 콘텐츠 추가 성공: playlistContentId={}, playlistId={}, contentId={}, requesterId={}",
        playlistContent.getId(), playlistId, contentId, requesterId);

    eventPublisher.publishEvent(new PlaylistContentAddedEvent(playlistId, contentId));
  }

  @Transactional
  public void removeContent(UUID playlistId, UUID contentId, UUID requesterId) {
    log.debug("플레이리스트에 콘텐츠 삭제 시도: playlistId={}, contentId={}, requesterId={}",
        playlistId, contentId, requesterId);

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PLAYLIST_CONTENT_PLAY_NOT_FOUND,
            Map.of("playlistId", playlistId)));

    // 소유자 검증
    validateOwner(playlist, requesterId);

    // 플레이리스트에 콘텐츠 존재 검증
    PlaylistContent playlistContent = playlistContentRepository.findByPlaylistIdAndContentId(
            playlistId, contentId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.UN_PLAYLIST_CONTENT_NOT_FOUND,
            Map.of("playlistId", playlistId, "contentId", contentId)));

    log.info("플레이리스트에 콘텐츠 삭제 성공: playlistContentId={}, playlistId={}, contentId={}, requesterId={}",
        playlistContent.getId(), playlistId, contentId, requesterId);
    playlistContentRepository.delete(playlistContent);
  }

  private UserSummary toUserSummary(User user) {
    return new UserSummary(
        user.getId(),
        user.getName(),
        user.getProfileImageUrl()
    );
  }

  private void validateOwner(Playlist playlist, UUID requesterId) {
    UUID ownerId = playlist.getOwner().getId();
    if (!ownerId.equals(requesterId)) {
      throw new PlaylistException(
          PlaylistErrorCode.PLAYLIST_FORBIDDEN,
          Map.of("ownerId", ownerId, "requesterId", requesterId)
      );
    }
  }
}
