package com.codeit.mople.domain.watchingsession.service;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.domain.watchingsession.dto.CursorResponseWatchingSessionDto;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionChange;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionContentDto;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionResponse;
import com.codeit.mople.global.dto.UserSummary;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Generated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Generated
public class WatchingSessionService {

  private final ContentRepository contentRepository;
  private final UserRepository userRepository;
  private final RedisTemplate<String, Object> redisTemplate;
  private final SimpMessagingTemplate messagingTemplate;

  private static final String USER_WATCHING_KEY_PREFIX = "user:watching:";
  private static final String CONTENT_WATCHERS_KEY_PREFIX = "content:watchers:";

  @Transactional(readOnly = true)
  public CursorResponseWatchingSessionDto getWatchingSessions(
      UUID contentId, String watcherNameLike, String cursor, UUID idAfter,
      int limit, String sortDirection, String sortBy) {
    log.debug("시청 세션 목록 조회 시작 - contentId: {}", contentId);

    //콘텐츠 존재 여부 예외 처리
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("contentId", contentId)));

    //limit 검증
    if (limit <= 0 || limit > 100) {
      throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST, Map.of("limit", limit));
    }

    //정렬 기준 및 정렬 방향 정규화
    if (sortBy == null) {
      sortBy = "id";
    }
    //sortBy가 id가 아닌 다른 정렬 값이 오면 거부
    if (!"id".equalsIgnoreCase(sortBy)) {
      throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST, Map.of("sortBy", sortBy));
    }
    if (sortDirection == null) {
      sortDirection = "ASCENDING";
    }

    //정렬 기준 및 정렬 방향 검증
    if (!"ASCENDING".equalsIgnoreCase(sortDirection) &&
        !"DESCENDING".equalsIgnoreCase(sortDirection)) {
      throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST, Map.of("sortDirection", sortDirection));
    }

    //커서 쌍 검증
    if ((cursor == null) != (idAfter == null)) {
      throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST,
          Map.of("cursor", String.valueOf(cursor), "idAfter", String.valueOf(idAfter)));
    }

    //Redis에서 현재 실시간으로 시청 중인 유저 ID 전체 목록 조회
    Set<UUID> watcherIds = getWatcherIds(contentId);

    //이름 검색 조건이 있을 경우 메모리 필터링
    if (watcherNameLike != null && !watcherNameLike.trim().isEmpty()) {
      List<User> matchingUsers = userRepository.findAllById(watcherIds).stream()
          .filter(user -> user.getName() != null && user.getName().contains(watcherNameLike.trim()))
          .toList();

      //필터링된 유저들의 ID로 watcherIds 교체
      watcherIds = matchingUsers.stream()
          .map(User::getId)
          .collect(Collectors.toSet());
    }

    //Redis Set은 순서가 없으므로 정렬 방향에 맞춰 리스트로 변환 및 정렬(메모리 정렬)
    boolean isDesc = "DESCENDING".equalsIgnoreCase(sortDirection);
    List<String> watcherIdList = watcherIds.stream()
        .map(UUID::toString)
        .sorted(isDesc ? Collections.reverseOrder() : String::compareTo)
        .toList();

    //전체 데이터 수 카운트
    long totalCount = watcherIdList.size();

    //커서 위치 탐색(idAfter 기준)
    //커서 유저가 퇴장해서 idAfter를 찾아내지 못한 경우(-1) 정렬 위치 계산
    int startIndex = 0;
    if (idAfter != null) {
      String targetId = idAfter.toString();
      int foundIndex = watcherIdList.indexOf(targetId);
      if (foundIndex != -1) {
        startIndex = foundIndex + 1; //커서 다음 항목부터 시작
      } else {
        // 유저가 퇴장하여 커서 ID를 찾지 못한 경우 정렬 위치 보정
        for (int i = 0; i < watcherIdList.size(); i++) {
          int cmp = watcherIdList.get(i).compareTo(targetId);
          if ((isDesc && cmp < 0) || (!isDesc && cmp > 0)) {
            startIndex = i;
            break;
          }
          if (i == watcherIdList.size() - 1) {
            startIndex = watcherIdList.size();
          }
        }
      }
    }

    //hasNext 판단 및 limit 사이즈만큼 자르기(Slicing)
    int endIndex = Math.min(startIndex + limit + 1, watcherIdList.size());
    List<String> pagedIdStrings = startIndex < watcherIdList.size()
        ? watcherIdList.subList(startIndex, endIndex)
        : Collections.emptyList();

    boolean hasNext = pagedIdStrings.size() > limit;
    List<String> resultIds = hasNext ? pagedIdStrings.subList(0, limit) : pagedIdStrings;

    //페이징된 target UUID 리스트 추출
    List<UUID> targetUserIds = resultIds.stream().map(UUID::fromString).toList();

    //DB에서 페이징 대상 유저들을 한 번에 조회하여 Map으로 캐싱 (N+1 방지)
    Map<UUID, User> userMap = userRepository.findAllById(targetUserIds).stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));

    //콘텐츠 정보를 담을 DTO 생성
    WatchingSessionContentDto contentDto = new WatchingSessionContentDto(
        content.getId(), content.getType().name(), content.getTitle(),
        content.getDescription(), content.getThumbnailUrl(), content.getTags(),
        content.calculateAverageRating(), content.getReviewCount()
    );

    //Redis 데이터 -> response dto 매핑
    List<WatchingSessionResponse> responses = resultIds.stream().map(idStr -> {
      UUID uId = UUID.fromString(idStr);
      User user = userMap.get(uId);

      //유저가 DB에 없을 경우를 대비한 Null-safe 방어 로직
      String name = user != null ? user.getName() : "알 수 없는 유저";
      String profileImageUrl = user != null ? user.getProfileImageUrl() : null;

      UserSummary userSummary = new UserSummary(uId, name, profileImageUrl);

      return new WatchingSessionResponse(
          UUID.randomUUID(), //실시간 세션 식별용 임시 ID
          Instant.now(),
          userSummary,
          contentDto
      );
    }).toList();

    //다음 커서 값 추출
    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext && !resultIds.isEmpty()) {
      String lastId = resultIds.get(resultIds.size() - 1);
      nextCursor = lastId;
      nextIdAfter = UUID.fromString(lastId);
    }

    //최종 CursorResponse DTO 반환
    return new CursorResponseWatchingSessionDto(
        responses,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy,
        sortDirection
    );
  }

  //유저가 콘텐츠 시청을 시작(입장)할 때 실시간 세션을 Redis에 기록하고 DB 갱신
  @Transactional
  public Long enterSession(UUID userId, UUID contentId) {
    String userKey = USER_WATCHING_KEY_PREFIX + userId.toString();
    String contentKey = CONTENT_WATCHERS_KEY_PREFIX + contentId.toString();

    //Redis 세션 전환을 재시도 가능한 트랜잭션(SessionCallback)으로 원자적 처리
    String previousContentId = null;
    boolean txSuccess = false;
    int maxRetries = 5;

    for (int i = 0; i < maxRetries; i++) {
      Object result = redisTemplate.execute(new SessionCallback<Object>() {
        @Override
        public Object execute(RedisOperations operations) {
          //해당 키 변경을 감시
          operations.watch(userKey);
          String prevId = (String) operations.opsForValue().get(userKey);

          //트랜잭션 시작
          operations.multi();
          if (prevId != null && !prevId.equals(contentId.toString())) {
            operations.opsForSet().remove(CONTENT_WATCHERS_KEY_PREFIX + prevId, userId.toString());
          }
          operations.opsForValue().set(userKey, contentId.toString());
          operations.opsForSet().add(contentKey, userId.toString());

          //트랜잭션 실행(다른 곳에서 키가 변경되었다면 execResult는 null이 됨)
          List<Object> execResult = operations.exec();
          if (execResult == null || execResult.isEmpty()) {
            return null;
          }
          return prevId == null ? "NULL_PREV" : prevId;
        }
      });

      if (result != null) {
        txSuccess = true;
        previousContentId = "NULL_PREV".equals(result) ? null : (String) result;
        break;
      }
    }

    if (!txSuccess) {
      log.error("입장 Redis 트랜잭션 실패 (최대 재시도 초과) - userId: {}", userId);
      throw new RuntimeException("일시적인 오류가 발생했습니다. 다시 시도해주세요.");
    }

    //유저가 다른 콘텐츠를 보고 있었다면 이전 기록 삭제(방 이동 고려)
    if (previousContentId != null && !previousContentId.equals(contentId.toString())) {
      String prevContentKey = CONTENT_WATCHERS_KEY_PREFIX + previousContentId;

      Long prevCount = redisTemplate.opsForSet().size(prevContentKey);
      Long watcherCount = prevCount != null ? prevCount : 0L;

      contentRepository.findById(UUID.fromString(previousContentId))
          .ifPresent(prevContent -> prevContent.updateWatcherCount(watcherCount));

      //이전 방 방 이동 퇴장(LEAVE) 이벤트 브로드캐스팅
      WatchingSessionChange prevChangeEvent = new WatchingSessionChange(
          previousContentId,
          userId,
          "LEAVE",
          watcherCount
      );
      messagingTemplate.convertAndSend(
          "/sub/contents/" + previousContentId + "/watch", prevChangeEvent);
    }

    //현재 해당 콘텐츠를 보고 있는 총 시청자 수 반환
    Long watcherCount = redisTemplate.opsForSet().size(contentKey);

    //현재 입장한 콘텐츠 DB 시청자 수 동기화
    contentRepository.findById(contentId)
        .ifPresent(content -> content.updateWatcherCount(
            watcherCount != null ? watcherCount : 0L));

    //웹소켓으로 입장 이벤트 브로드캐스팅
    WatchingSessionChange changeEvent = new WatchingSessionChange(
        contentId.toString(),
        userId,
        "ENTER",
        watcherCount
    );
    messagingTemplate.convertAndSend(
        "/sub/contents/" + contentId.toString() + "/watch", changeEvent);

    return watcherCount;
  }

  //유저가 콘텐츠 시청을 종료(퇴장)할 때 Redis에서 세션을 제거하고 DB 갱신
  @Transactional
  public Long leaveSession(UUID userId, UUID contentId) {
    String userKey = USER_WATCHING_KEY_PREFIX + userId.toString();
    String contentKey = CONTENT_WATCHERS_KEY_PREFIX + contentId.toString();

    //유저가 현재 시청 중인 콘텐츠가 퇴장 요청 콘텐츠와 일치하는지 확인 및 삭제를 원자적으로 처리
    boolean txSuccess = false;
    boolean wasWatching = false;
    int maxRetries = 5;

    for (int i = 0; i < maxRetries; i++) {
      Object result = redisTemplate.execute(new SessionCallback<Object>() {
        @Override
        public Object execute(RedisOperations operations) {
          operations.watch(userKey);
          String currentWatchingId = (String) operations.opsForValue().get(userKey);

          //현재 시청중인 컨텐츠가 아니면 트랜잭션 취소
          if (currentWatchingId == null || !currentWatchingId.equals(contentId.toString())) {
            operations.unwatch();
            return "NOT_WATCHING";
          }

          operations.multi();
          operations.delete(userKey);
          operations.opsForSet().remove(contentKey, userId.toString());

          List<Object> execResult = operations.exec();
          if (execResult == null || execResult.isEmpty()) {
            return null; // 충돌 발생, 재시도
          }
          return "SUCCESS";
        }
      });

      if (result != null) {
        txSuccess = true;
        wasWatching = !"NOT_WATCHING".equals(result);
        break;
      }
    }

    if (!txSuccess) {
      log.error("퇴장 Redis 트랜잭션 실패 (최대 재시도 초과) - userId: {}", userId);
      throw new RuntimeException("일시적인 오류가 발생했습니다. 다시 시도해주세요.");
    }

    if (!wasWatching) {
      log.warn("퇴장 요청 무시: 유저가 해당 콘텐츠를 시청 중이지 않음. userId: {}, contentId: {}", userId, contentId);
      Long currentCount = redisTemplate.opsForSet().size(contentKey);
      return currentCount != null ? currentCount : 0L;
    }

    //퇴장 후 남은 총 시청자 수 반환(키가 만료되거나 없으면 0반환)
    Long remainingCount = redisTemplate.opsForSet().size(contentKey);
    Long watcherCount = remainingCount != null ? remainingCount : 0L;

    //퇴장한 콘텐츠 DB 시청자 수 동기화
    contentRepository.findById(contentId)
        .ifPresent(content -> content.updateWatcherCount(watcherCount));

    //웹소켓으로 퇴장 이벤트 브로드캐스팅
    WatchingSessionChange changeEvent = new WatchingSessionChange(
        contentId.toString(),
        userId,
        "LEAVE",
        watcherCount
    );
    messagingTemplate.convertAndSend(
        "/sub/contents/" + contentId.toString() + "/watch", changeEvent);

    return watcherCount;
  }

  //특정 유저가 현재 시청 중인 콘텐츠의 ID를 조회
  //시청 세션이 없을 경우 404 예외 던짐
  public UUID getWatchingContentId(UUID userId) {
    //유저 존재 여부 확인
    userRepository.findById(userId)
        .orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("userId", userId)));

    String userKey = USER_WATCHING_KEY_PREFIX + userId.toString();
    String contentIdStr = (String) redisTemplate.opsForValue().get(userKey);

    if (contentIdStr == null) {
      throw new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("watcherId", userId));
    }

    return UUID.fromString(contentIdStr);
  }

  //특정 콘텐츠를 현재 실시간으로 시청 중인 유저 ID 목록을 조회
  public Set<UUID> getWatcherIds(UUID contentId) {
    String contentKey = CONTENT_WATCHERS_KEY_PREFIX + contentId.toString();
    Set<Object> members = redisTemplate.opsForSet().members(contentKey);

    if (members == null || members.isEmpty()) {
      return Collections.emptySet();
    }

    return members.stream()
        .map(member -> UUID.fromString((String) member))
        .collect(Collectors.toSet());
  }
}