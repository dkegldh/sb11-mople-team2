package com.codeit.mople.domain.playlist.service;

import com.codeit.mople.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.mople.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.mople.domain.playlist.dto.response.PlaylistContentResponse;
import com.codeit.mople.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.entity.PlaylistSubscription;
import com.codeit.mople.domain.playlist.event.PlaylistSubscriptionCreateEvent;
import com.codeit.mople.domain.playlist.exception.PlaylistErrorCode;
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

    log.debug("플레이리스트 구독 시도: playlistId={}, subscriberId={}", playlistId, subscriberId);

    // 존재확인
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new CustomException(PlaylistErrorCode.PLAYLIST_NOT_FOUND));

    // 본인 구독 차단
    if (subscriberId.equals(playlist.getOwner().getId())) {
      throw new CustomException(PlaylistErrorCode.PLAYLIST_DUPLICATE);
    }

    // 존재확인
    User subscriber = userRepository.findById(subscriberId)
        .orElseThrow(() -> new CustomException(PlaylistErrorCode.PLAYLIST_NOT_FOUND));

    // 중복 구독 차단
    if (playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId, subscriberId)) {
      throw new CustomException(PlaylistErrorCode.PLAYLIST_DUPLICATE);
    }

    PlaylistSubscription saved = playlistSubscriptionRepository.save(
        PlaylistSubscription.create(playlist, subscriber));

    log.info("플레이리스트 구독 성공: playlistSubscriptionId={}, playlistId={}, subscriberId={}",
        saved.getId(), playlistId, subscriberId);

    publisher.publishEvent(new PlaylistSubscriptionCreateEvent(playlistId, subscriberId));
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
