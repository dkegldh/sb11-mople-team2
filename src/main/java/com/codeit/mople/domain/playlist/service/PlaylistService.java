package com.codeit.mople.domain.playlist.service;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.PlaylistSortBy;
import com.codeit.mople.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.mople.domain.playlist.dto.response.PlaylistContentResponse;
import com.codeit.mople.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.entity.PlaylistContent;
import com.codeit.mople.domain.playlist.entity.PlaylistSubscription;
import com.codeit.mople.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistSearchIndexDeleteEvent;
import com.codeit.mople.domain.playlist.event.PlaylistSearchIndexEvent;
import com.codeit.mople.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistUnsubscribedEvent;
import com.codeit.mople.domain.playlist.exception.PlaylistErrorCode;
import com.codeit.mople.domain.playlist.exception.PlaylistException;
import com.codeit.mople.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.mople.domain.playlist.repository.PlaylistRepository;
import com.codeit.mople.domain.playlist.repository.PlaylistSubscriptionRepository;
import com.codeit.mople.domain.playlist.repository.search.PlaylistSearchRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.CacheNames;
import com.codeit.mople.global.dto.CursorResponse;
import com.codeit.mople.global.dto.SearchResult;
import com.codeit.mople.global.dto.UserSummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
  private final ApplicationEventPublisher publisher;
  private final PlaylistSearchRepository searchRepository;

  private final MeterRegistry meterRegistry;
  private Counter playlistCreateCounter;
  private Counter playlistSubscribeCounter;
  private Counter playlistContentAddCounter;

  @PostConstruct
  public void initMetrics() {
    this.playlistCreateCounter = Counter.builder("mople.playlist.create.count").register(meterRegistry);
    this.playlistSubscribeCounter = Counter.builder("mople.playlist.subscribe.count").register(meterRegistry);
    this.playlistContentAddCounter = Counter.builder("mople.playlist.content.add.count").register(meterRegistry);
  }

  @CacheEvict(value = CacheNames.PLAYLIST_LIST, allEntries = true)
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

    publisher.publishEvent(
        new PlaylistCreatedEvent(ownerId, owner.getName(), savedPlaylist.getTitle())
    );

    publisher.publishEvent(
        new PlaylistSearchIndexEvent(
            UUID.randomUUID(),
            savedPlaylist.getCreatedAt(),
            savedPlaylist.getId(),
            savedPlaylist.getTitle(),
            savedPlaylist.getUpdatedAt(),
            savedPlaylist.getSubscriberCount()
        )
    );

    //생성 완료 후 카운트
    playlistCreateCounter.increment();

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
  @Cacheable(
      cacheNames = CacheNames.PLAYLIST_LIST,
      key = "{"
          + "#condition.keywordLike(),"
          + "#condition.ownerIdEqual(),"
          + "#condition.subscriberIdEqual(), "
          + "#condition.cursor(),"
          + "#condition.idAfter(),"
          + "#condition.limit(), "
          + "#condition.sortBy(),"
          + "#condition.sortDirection(),"
          + "#requesterId"
          + "}"
  )
  @Transactional(readOnly = true)
  public CursorResponse<PlaylistResponse> findAll(PlaylistQueryCondition condition,
      UUID requesterId) {

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

    SearchResult searchResult = null;

    // 검색 키워드가 있을 경우 Elasticsearch에서 커서 페이지네이션
    if (condition.keywordLike() != null && !condition.keywordLike().isBlank()) {
      searchResult =
          searchRepository.findAllByTitleContainingIgnoreCase(
              condition.keywordLike(),
              condition.idAfter(),
              parseCursor(condition.cursor(), condition.sortBy()),
              condition.limit(),
              condition.sortBy(),
              condition.sortDirection()
          );
    }

    // 목록 조회(limit + 1까지)
    List<Playlist> playlists =
        searchResult == null
            ? playlistRepository.findAll(condition, null)
            : playlistRepository.findPlaylistsByIds(
                searchResult.ids(),
                condition
            );

    // 목록 조회된 Playlist의 총 개수
    long totalCount =
        searchResult == null
            ? playlistRepository.count(condition, null)
            : searchResult.totalCount();

    // Elasticsearch 검색 결과 순서 유지
    if (searchResult != null) {
      Map<UUID, Playlist> playlistMap = playlists.stream()
          .collect(Collectors.toMap(
              Playlist::getId,
              Function.identity()
          ));

      playlists = searchResult.ids().stream()
          .map(playlistMap::get)
          .filter(Objects::nonNull)
          .toList();
    }

    // 조회된 플레이리스트 ID
    List<UUID> playlistIds = playlists.stream()
        .map(Playlist::getId)
        .toList();

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

    CursorResponse<PlaylistResponse> response;

    if (searchResult != null) {
      response = CursorResponse.ofSearchResult(
          data,
          searchResult.nextCursor(),
          searchResult.nextIdAfter(),
          searchResult.hasNext(),
          searchResult.totalCount(),
          condition.sortBy().getValue(),
          condition.sortDirection().name()
      );
    } else {
      response = CursorResponse.of(
          data,
          condition.limit(),
          totalCount,
          condition.sortBy().getValue(),
          condition.sortDirection().name(),
          playlist -> {
            // 정렬조건
            if (condition.sortBy() == PlaylistSortBy.UPDATED_AT) {
              return String.valueOf(playlist.updatedAt());
            } else if (condition.sortBy() == PlaylistSortBy.SUBSCRIBE_COUNT) {
              return String.valueOf(playlist.subscriberCount());
            }

            return null;
          },
          PlaylistResponse::id
      );
    }

    log.info("플레이리스트 목록 조회 완료: size={}, totalCount={}, hasNext={}",
        data.size(), response.totalCount(), response.hasNext());

    return response;
  }

  // 플레이리스트 수정
  @CacheEvict(cacheNames = CacheNames.PLAYLIST_LIST, allEntries = true)
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

    publisher.publishEvent(
        new PlaylistSearchIndexEvent(
            UUID.randomUUID(),
            Instant.now(),
            playlist.getId(),
            playlist.getTitle(),
            playlist.getUpdatedAt(),
            playlist.getSubscriberCount()
        )
    );

    return response;
  }

  // 플레이리스트 삭제
  @CacheEvict(cacheNames = CacheNames.PLAYLIST_LIST, allEntries = true)
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

    // 플레이리스트 구독 정보 삭제
    playlistSubscriptionRepository.deleteAllByPlaylistId(playlistId);

    // deleteById도 가능하지만 where id=로 조회 후 delete하기 때문에(불필요한 조회가 발생함) 조회 실행을 뺌
    playlistRepository.delete(playlist);

    log.info("플레이리스트 삭제 완료: playlistId={}, ownerId={}",
        playlistId, ownerId);

    publisher.publishEvent(
        new PlaylistSearchIndexDeleteEvent(
            UUID.randomUUID(),
            Instant.now(),
            playlistId
        )
    );

  }

  @CacheEvict(cacheNames = CacheNames.PLAYLIST_LIST, allEntries = true)
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

    log.info("플레이리스트 구독 성공: playlistSubscriptionId={}, playlistId={}, subscriberId={}",
        saved.getId(), playlistId, subscriberId);

    publisher.publishEvent(new PlaylistSubscribedEvent(
        UUID.randomUUID(),
        saved.getCreatedAt(),
        ownerId,
        playlistId,
        subscriberId,
        subscriber.getName(),
        playlist.getTitle()
    ));

    //구독 성공 후 카운트
    playlistSubscribeCounter.increment();
  }

  @CacheEvict(cacheNames = CacheNames.PLAYLIST_LIST, allEntries = true)
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

    publisher.publishEvent(new PlaylistUnsubscribedEvent(
        UUID.randomUUID(),
        Instant.now(),
        playlistId,
        subscriberId
    ));

    log.info("플레이리스트 구독 취소 성공: playlist={}, subscriberId={}",
        playlistId, subscriberId);
  }

  @CacheEvict(cacheNames = CacheNames.PLAYLIST_LIST, allEntries = true)
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

    PlaylistContent saved = playlistContentRepository.save(
        PlaylistContent.create(playlist, content));

    log.info("플레이리스트에 콘텐츠 추가 성공: playlistContentId={}, playlistId={}, contentId={}, requesterId={}",
        saved.getId(), playlistId, contentId, requesterId);

    publisher.publishEvent(new PlaylistContentAddedEvent(
        UUID.randomUUID(),
        saved.getCreatedAt(),
        saved.getId(),
        playlistId,
        contentId,
        playlist.getTitle()
    ));

    //플레이리스트에 콘텐츠 추가 성공 후 카운트
    playlistContentAddCounter.increment();
  }

  @CacheEvict(cacheNames = CacheNames.PLAYLIST_LIST, allEntries = true)
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

  @Transactional(readOnly = true)
  public List<UUID> getSubscriberIds(UUID playlistId) {
    log.debug("구독자 id 목록 조회: playlistId={}", playlistId);
    return playlistSubscriptionRepository.findSubscriberIdsByPlaylistId(playlistId);
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

  private Object parseCursor(String cursor, PlaylistSortBy sortBy) {
    if (cursor == null) {
      return null;
    }

    if (cursor.isBlank()) {
      throw new PlaylistException(
          PlaylistErrorCode.PLAYLIST_INVALID_CURSOR,
          Map.of("cursor", cursor)
      );
    }

    try {
      return switch (sortBy) {
        case UPDATED_AT -> Instant.parse(cursor);

        case SUBSCRIBE_COUNT -> {
          long parsed = Long.parseLong(cursor);

          if (parsed < 0) {
            throw new PlaylistException(
                PlaylistErrorCode.PLAYLIST_INVALID_CURSOR,
                Map.of("cursor", cursor)
            );
          }

          yield parsed;
        }
      };
    } catch (DateTimeParseException | NumberFormatException e) {
      throw new PlaylistException(
          PlaylistErrorCode.PLAYLIST_INVALID_CURSOR,
          Map.of("cursor", cursor)
      );
    }
  }
}
