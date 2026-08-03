package com.codeit.mople.domain.playlist.service;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.mople.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.mople.domain.playlist.dto.response.PlaylistContentResponse;
import com.codeit.mople.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.entity.PlaylistContent;
import com.codeit.mople.domain.playlist.entity.PlaylistSubscription;
import com.codeit.mople.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.mople.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.mople.domain.playlist.exception.PlaylistErrorCode;
import com.codeit.mople.domain.playlist.exception.PlaylistException;
import com.codeit.mople.domain.playlist.exception.PlaylistForbiddenException;
import com.codeit.mople.domain.playlist.exception.PlaylistNotFoundException;
import com.codeit.mople.domain.playlist.mapper.PlaylistContentMapper;
import com.codeit.mople.domain.playlist.mapper.PlaylistMapper;
import com.codeit.mople.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.mople.domain.playlist.repository.PlaylistRepository;
import com.codeit.mople.domain.playlist.repository.PlaylistSubscriptionRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.dto.UserSummary;
import com.codeit.mople.global.error.CustomException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
  private final PlaylistContentMapper playlistContentMapper;
  private final PlaylistMapper mapper;

  private final ApplicationEventPublisher publisher;

  @Transactional
  public PlaylistResponse create(UUID ownerId, PlaylistCreateRequest request) {

    log.debug("플레이리스트 생성 시도: ownerId={}, title={}",
        ownerId, request.title());

    // TODO 김명근: User 예외 계층 생성 시 리팩토링
    User owner = userRepository.findById(ownerId).orElseThrow(() ->
        new CustomException(UserErrorCode.USER_NOT_FOUND));

    Playlist playlist = Playlist.create(owner, request.title(), request.description());

    Playlist savedPlaylist = playlistRepository.save(playlist);

    UserSummary ownerResponse = toUserSummary(owner);

    PlaylistResponse response = mapper.toResponse(
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
  public PlaylistResponse find(UUID playlistId) {

    log.debug("플레이리스트 조회 시도: playlistId={}",
        playlistId);

    Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() ->
        new PlaylistNotFoundException(playlistId)
    );

    UserSummary ownerResponse = toUserSummary(playlist.getOwner());

    // 콘텐츠를 플레이리스트에 추가한 순서대로 표시(콘텐츠를 B, E, A, C 순으로 추가했을 경우 추가한 순서 그대로)
    List<PlaylistContentResponse> contents =
        playlistContentRepository.findAllByPlaylistIdOrderByCreatedAtAsc(playlistId).stream()
            .map(playlistContentMapper::toResponse)
            .toList();

    PlaylistResponse response = mapper.toResponse(
        playlist,
        ownerResponse,
        false,
        contents);

    log.info("플레이리스트 조회 완료: playlistId={}, contentCount={}",
        playlistId, contents.size());

    return response;
  }

  @Transactional
  public PlaylistResponse update(
      UUID playlistId,
      PlaylistUpdateRequest request,
      UUID userId
  ) {

    log.debug("플레이리스트 수정 시도: playlistId={}, userId={}",
        playlistId, userId);

    // Playlist 조회
    Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() ->
        new PlaylistNotFoundException(playlistId)
    );

    // 플레이리스트 소유자가 맞는지 검증
    validateOwner(playlist, userId);

    playlist.update(request.title(), request.description());

    UserSummary ownerResponse = toUserSummary(playlist.getOwner());

    List<PlaylistContentResponse> contents =
        playlistContentRepository.findAllByPlaylistIdOrderByCreatedAtAsc(playlistId).stream()
            .map(playlistContentMapper::toResponse)
            .toList();

    PlaylistResponse response = mapper.toResponse(
        playlist,
        ownerResponse,
        false,
        contents
    );

    log.info("플레이리스트 수정 완료: playlistId={}, userId={}",
        playlistId, userId);

    return response;
  }

  @Transactional
  public void delete(UUID playlistId, UUID userId) {

    log.debug("플레이리스트 삭제 시도: playlistId={}, userId={}",
        playlistId, userId);

    Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() ->
        new PlaylistNotFoundException(playlistId)
    );

    validateOwner(playlist, userId);

    // 플레이리리스트 삭제 전 플레이리스트 안에 들어있던 컨텐츠들을 삭제
    playlistContentRepository.deleteAllByPlaylistId(playlistId);

    // deleteById도 가능하지만 where id=로 조회 후 delete하기 때문에(불필요한 조회가 발생함) 조회 실행을 뺌
    playlistRepository.delete(playlist);

    log.info("플레이리스트 삭제 완료: playlistId={}, userId={}",
        playlistId, userId);

  }

  @Transactional
  public void subscribe(UUID playlistId, UUID subscriberId) {

    log.debug("플레이리스트 구독 시도: playlistId={}, subscriberId={}",
        playlistId, subscriberId);

    // 존재확인
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.SUBSCRIBE_NOT_FOUND));

    UUID ownerId = playlist.getOwner().getId();

    // 본인 구독 차단
    if (subscriberId.equals(ownerId)) {
      throw new PlaylistException(PlaylistErrorCode.SUBSCRIBE_NOT_ALLOWED);
    }

    // 존재확인
    User subscriber = userRepository.findById(subscriberId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.SUBSCRIBE_UNAUTHORIZED));

    // 중복 구독 차단
    if (playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId, subscriberId)) {
      throw new PlaylistException(PlaylistErrorCode.SUBSCRIBE_DUPLICATE);
    }

    PlaylistSubscription saved = playlistSubscriptionRepository.save(
        PlaylistSubscription.create(playlist, subscriber));
    playlistRepository.increaseSubscriberCount(playlistId);

    log.info("플레이리스트 구독 성공: playlistSubscriptionId={}, playlistId={}, subscriberId={}",
        saved.getId(), playlistId, subscriberId);

    publisher.publishEvent(new PlaylistSubscribedEvent(ownerId, playlistId, subscriberId));
  }

  @Transactional
  public void unSubscribe(UUID playlistId, UUID subscriberId) {
    log.debug("플레이리스트 구독 취소 시도: playlistId={}, subscriberId={}",
        playlistId, subscriberId);

    // 구독 존재 검증 + 삭제
    int deleted = playlistSubscriptionRepository.deleteByPlaylistIdAndSubscriberId(playlistId, subscriberId);
    if (deleted == 0) {
      throw new PlaylistException(PlaylistErrorCode.UNSUBSCRIBE_NOT_FOUND,
          Map.of("playlistId", playlistId, "subscriberId", subscriberId));
    }
    int decreased = playlistRepository.decreaseSubscriberCount(playlistId);
    if (decreased == 0) {
      log.warn("구독자 수 감소 실패(이미 0): playlistId={}, subscriberId={}", playlistId, subscriberId);
    }

    log.info("플레이리스트 구독 취소 성공: playlist={}, subscriberId={}",
        playlistId, subscriberId);
  }

  @Transactional
  public void addContent(UUID playlistId, UUID contentId, UUID ownerId) {
    log.debug("플레이리스트에 콘텐츠 추가 시도: playlistId={}, contentId={}, ownerId={}",
        playlistId, contentId, ownerId);

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PY_CONTENT_PLAY_NOT_FOUND, Map.of("playlistId", playlistId)));
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PY_CONTENT_CONTENT_NOT_FOUND, Map.of("contentId", contentId)));
    // 소유자 검증
    validateOwner(playlist, ownerId);

    // 중복 검증
    if (playlistContentRepository.existsByPlaylistIdAndContentId(playlistId, contentId)) {
      throw new PlaylistException(PlaylistErrorCode.PY_CONTENT_DUPLICATE);
    }

    PlaylistContent playlistContent = PlaylistContent.create(playlist, content);
    playlistContentRepository.save(playlistContent);

    log.info("플레이리스트에 콘텐츠 추가 성공: playlistContentId={}, playlistId={}, contentId={}, ownerId={}",
        playlistContent.getId(), playlistId, contentId, ownerId);

    publisher.publishEvent(new PlaylistContentAddedEvent(playlistId, contentId));
  }

  @Transactional
  public void removeContent(UUID playlistId, UUID contentId, UUID ownerId) {
    log.debug("플레이리스트에 콘텐츠 삭제 시도: playlistId={}, contentId={}, ownerId={}",
        playlistId, contentId, ownerId);

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PY_CONTENT_PLAY_NOT_FOUND, Map.of("playlistId", playlistId)));

    // 소유자 검증
    validateOwner(playlist, ownerId);

    // 플레이리스트에 콘텐츠 존재 검증
    PlaylistContent playlistContent = playlistContentRepository.findByPlaylistIdAndContentId(playlistId, contentId)
        .orElseThrow(() -> new PlaylistException(PlaylistErrorCode.UN_PY_CONTENT_NOT_FOUND, Map.of("playlistId", playlistId, "contentId", contentId)));

    log.info("플레이리스트에 콘텐츠 삭제 성공: playlistContentId={}, playlistId={}, contentId={}, ownerId={}",
        playlistContent.getId(), playlistId, contentId, ownerId);
    playlistContentRepository.delete(playlistContent);
  }

  private UserSummary toUserSummary(User user) {
    return new UserSummary(
        user.getId(),
        user.getName(),
        user.getProfileImageUrl()
    );
  }

  private void validateOwner(Playlist playlist, UUID userId) {
    if (!playlist.getOwner().getId().equals(userId)) {
      throw new PlaylistForbiddenException(playlist.getId());
    }
  }
}
