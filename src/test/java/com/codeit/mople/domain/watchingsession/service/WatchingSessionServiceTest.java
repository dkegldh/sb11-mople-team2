package com.codeit.mople.domain.watchingsession.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.domain.watchingsession.dto.ContentChatDto;
import com.codeit.mople.domain.watchingsession.dto.ContentChatSendRequest;
import com.codeit.mople.domain.watchingsession.dto.CursorResponseWatchingSessionDto;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionEvent;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionResponse;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
public class WatchingSessionServiceTest {

  @InjectMocks
  private WatchingSessionService watchingSessionService;

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private RedisTemplate<String, Object> redisTemplate;

  @Mock
  private SimpMessagingTemplate messagingTemplate; //채팅 검증용

  @Mock
  private ApplicationEventPublisher eventPublisher; //이벤트 발행 검증용

  @Mock
  private CacheManager cacheManager;

  @Mock
  private ValueOperations<String, Object> valueOperations;

  @Mock
  private ZSetOperations<String, Object> zSetOperations;

  @BeforeEach
  void setUp() {
    //RedisTemplate 의 opsForValue, opsForZSet 호출 시 Mock 객체 반환하도록 설정
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
  }

  @Test
  @DisplayName("시청 세션 목록 조회 실패 - 콘텐츠가 존재하지 않음(404 예외 발생)")
  void getWatchingSessions_Fail_ContentNotFound() {
    UUID contentId = UUID.randomUUID();

    given(contentRepository.findById(contentId)).willReturn(Optional.empty());

    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, null, 10,
            "ASCENDING", "createdAt"));
  }

  @Test
  @DisplayName("시청 세션 목록 조회 성공 - Redis 데이터 기반 메모리 페이징 및 DB Hydration 확인")
  void getWatchingSessions_Success_WithData() {
    UUID contentId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String contentKey = "content:watchers:" + contentId;

    Content mockContent = mock(Content.class);
    User mockUser = mock(User.class);

    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));
    given(mockContent.getId()).willReturn(contentId);

    //ZSet에 1명의 유저가 접속 중이라고 가정(점수 포함 튜플 모킹)
    ZSetOperations.TypedTuple<Object> mockTuple = mock(ZSetOperations.TypedTuple.class);
    given(mockTuple.getValue()).willReturn(userId.toString());
    given(mockTuple.getScore()).willReturn((double) Instant.now().toEpochMilli());

    //zCard 및 커서 페이징 메서드 모킹 - 파라미터 타입 매칭 (double, double, long, long)
    given(zSetOperations.zCard(contentKey)).willReturn(1L);
    given(zSetOperations.reverseRangeByScoreWithScores(eq(contentKey), anyDouble(), anyDouble(), anyLong(), anyLong()))
        .willReturn(Set.of(mockTuple));

    //DB에서 해당 유저 정보 조회 모킹
    given(userRepository.findAllById(anyList())).willReturn(List.of(mockUser));

    //Redis multiGet으로 활성 세션 ID 일괄 조회 모킹
    given(valueOperations.multiGet(anyList())).willReturn(List.of(UUID.randomUUID().toString()));

    given(mockUser.getId()).willReturn(userId);
    given(mockContent.getType()).willReturn(ContentType.MOVIE);
    given(mockUser.getName()).willReturn("테스터");

    CursorResponseWatchingSessionDto result = watchingSessionService.getWatchingSessions(
        contentId, null, null, null, 10,
        "DESCENDING", "createdAt");

    assertNotNull(result);
    assertEquals(1, result.totalCount());
    assertFalse(result.hasNext());
    assertEquals(1, result.data().size());
    assertEquals("테스터", result.data().get(0).watcher().name());
  }

  @Test
  @DisplayName("시청 세션 목록 조회 성공 - watcherNameLike 키워드 검색 필터링 확인")
  void getWatchingSessions_Success_WithWatcherNameLike() {
    UUID contentId = UUID.randomUUID();
    UUID user1Id = UUID.randomUUID();
    UUID user2Id = UUID.randomUUID();
    String contentKey = "content:watchers:" + contentId;

    Content mockContent = mock(Content.class);
    User user1 = mock(User.class);
    User user2 = mock(User.class);

    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));

    ZSetOperations.TypedTuple<Object> tuple1 = mock(ZSetOperations.TypedTuple.class);
    given(tuple1.getValue()).willReturn(user1Id.toString());
    given(tuple1.getScore()).willReturn((double) Instant.now().toEpochMilli());

    ZSetOperations.TypedTuple<Object> tuple2 = mock(ZSetOperations.TypedTuple.class);
    given(tuple2.getValue()).willReturn(user2Id.toString());
    given(tuple2.getScore()).willReturn((double) Instant.now().toEpochMilli());

    //검색어가 있을 때는 예외적으로 reverseRangeWithScores를 사용
    given(zSetOperations.zCard(contentKey)).willReturn(1L);
    given(zSetOperations.reverseRangeWithScores(eq(contentKey), eq(0L), anyLong())).willReturn(Set.of(tuple1, tuple2));
    given(userRepository.findAllById(any())).willReturn(List.of(user1, user2));
    given(valueOperations.multiGet(anyList())).willReturn(List.of(UUID.randomUUID().toString()));

    given(user1.getId()).willReturn(user1Id);
    given(user2.getId()).willReturn(user2Id);

    given(user1.getName()).willReturn("홍길동");
    given(user2.getName()).willReturn("김철수");
    given(mockContent.getType()).willReturn(ContentType.MOVIE);

    CursorResponseWatchingSessionDto result = watchingSessionService.getWatchingSessions(
        contentId, "홍길", null, null, 10,
        "DESCENDING", "createdAt");

    assertNotNull(result);
    assertEquals(1, result.totalCount()); //"홍길동" 1명만 필터링되어야 함
    assertEquals("홍길동", result.data().get(0).watcher().name());
  }

  @Test
  @DisplayName("시청 세션 목록 조회 성공 - 동일 세션을 두 번 조회해도 랜덤값이 아닌 실제 Redis에 저장된 동일한 sessionUuid를 반환")
  void getWatchingSessions_Success_StableSessionId() {
    UUID contentId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String contentKey = "content:watchers:" + contentId;

    Content mockContent = mock(Content.class);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));
    given(mockContent.getId()).willReturn(contentId);
    given(mockContent.getType()).willReturn(ContentType.MOVIE); //NPE 방지

    ZSetOperations.TypedTuple<Object> mockTuple = mock(ZSetOperations.TypedTuple.class);
    given(mockTuple.getValue()).willReturn(userId.toString());
    given(mockTuple.getScore()).willReturn((double) Instant.now().toEpochMilli());

    //zCard 및 커서 페이징 메서드 모킹 적용 - 파라미터 타입 매칭
    given(zSetOperations.zCard(contentKey)).willReturn(1L);
    given(zSetOperations.reverseRangeByScoreWithScores(eq(contentKey), anyDouble(), anyDouble(), anyLong(), anyLong()))
        .willReturn(Set.of(mockTuple));

    //실제 Redis에 저장되어 있는 세션 ID를 모킹
    String expectedSessionUuid = UUID.randomUUID().toString();
    given(valueOperations.multiGet(anyList())).willReturn(List.of(expectedSessionUuid));

    CursorResponseWatchingSessionDto result1 = watchingSessionService.getWatchingSessions(
        contentId, null, null, null, 10, "DESCENDING", "createdAt");

    CursorResponseWatchingSessionDto result2 = watchingSessionService.getWatchingSessions(
        contentId, null, null, null, 10, "DESCENDING", "createdAt");

    //두 번의 조회 모두 동일한 ID가 반환되는지 검증
    assertEquals(expectedSessionUuid, result1.data().get(0).id().toString());
    assertEquals(expectedSessionUuid, result2.data().get(0).id().toString());
  }

  @Test
  @DisplayName("시청 세션 목록 조회 성공 - 커서 유저 퇴장 시 joinedAt과 userId 복합 비교를 통해 중복 없이 다음 항목을 찾는다")
  void getWatchingSessions_Success_CompositeCursorFallback() {
    UUID contentId = UUID.randomUUID();
    String contentKey = "content:watchers:" + contentId;
    Content mockContent = mock(Content.class);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));
    given(mockContent.getType()).willReturn(ContentType.MOVIE); //NPE 방지

    long sameTime = Instant.now().toEpochMilli();
    UUID user1Id = new UUID(0, 1);
    UUID user2Id = new UUID(0, 2);
    UUID user3Id = new UUID(0, 3);

    User user1 = mock(User.class);
    User user2 = mock(User.class);
    given(user1.getId()).willReturn(user1Id);
    given(user2.getId()).willReturn(user2Id);
    given(userRepository.findAllById(any())).willReturn(List.of(user1, user2));

    ZSetOperations.TypedTuple<Object> t1 = mock(ZSetOperations.TypedTuple.class);
    given(t1.getValue()).willReturn(user1Id.toString());
    given(t1.getScore()).willReturn((double) sameTime);

    ZSetOperations.TypedTuple<Object> t2 = mock(ZSetOperations.TypedTuple.class);
    given(t2.getValue()).willReturn(user2Id.toString());
    given(t2.getScore()).willReturn((double) sameTime);

    //커서였던 user3이 퇴장하여 현재 ZSet에는 user2와 user1만 남아있는 상황 모킹(역순 반환)
    Set<ZSetOperations.TypedTuple<Object>> returnedSet = new java.util.LinkedHashSet<>();
    returnedSet.add(t2);
    returnedSet.add(t1);

    //zCard 및 커서 페이징 메서드 모킹 적용 - 파라미터 타입 매칭
    given(zSetOperations.zCard(contentKey)).willReturn(2L);
    given(zSetOperations.reverseRangeByScoreWithScores(eq(contentKey), anyDouble(), anyDouble(), anyLong(), anyLong()))
        .willReturn(returnedSet);

    //이전 페이지 마지막 항목이었던 user3 정보를 커서로 전달
    String cursorStr = String.valueOf(sameTime);
    UUID idAfter = user3Id;

    given(valueOperations.multiGet(anyList())).willReturn(List.of(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString()
    ));

    CursorResponseWatchingSessionDto result = watchingSessionService.getWatchingSessions(
        contentId, null, cursorStr, idAfter, 10, "DESCENDING", "createdAt"
    );

    //퇴장한 user3 다음 순서인 user2부터 정상적으로 2개의 항목이 조회되어야 함
    assertEquals(2, result.data().size());
    assertEquals(user2Id.toString(), result.data().get(0).watcher().userId().toString());
    assertEquals(user1Id.toString(), result.data().get(1).watcher().userId().toString());
  }

  @Test
  @DisplayName("시청 세션 목록 조회 실패 - 지원하지 않는 정렬 기준(sortBy) 검증")
  void getWatchingSessions_Fail_InvalidSortBy() {
    UUID contentId = UUID.randomUUID();
    Content mockContent = mock(Content.class);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));

    //지원하지 않는 sortBy("id")로 요청 시 ContentException 발생 검증
    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, null, 10,
            "ASCENDING", "id"));
  }

  @Test
  @DisplayName("시청 세션 목록 조회 실패 - 지원하지 않는 정렬 방향(sortDirection) 검증")
  void getWatchingSessions_Fail_InvalidSortDirection() {
    UUID contentId = UUID.randomUUID();
    Content mockContent = mock(Content.class);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));

    //지원하지 않는 sortDirection("INVALID")으로 요청 시 ContentException 발생 검증
    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, null, 10,
            "INVALID", "createdAt"));
  }

  @Test
  @DisplayName("유저 입장 성공 - DB 검증 선행, Redis 기록 후 Event 발행 확인")
  void enterSession_Success() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String contentKey = "content:watchers:" + contentId;
    String sessionIdKey = "user:session:id:" + userId;
    String userKey = "user:watching:" + userId;

    Content mockContent = mock(Content.class);
    lenient().when(mockContent.getType()).thenReturn(ContentType.MOVIE);

    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));
    given(valueOperations.get(sessionIdKey)).willReturn(null);

    given(valueOperations.get(userKey)).willReturn(null);
    given(redisTemplate.exec()).willReturn(List.of(new Object()));
    given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
      SessionCallback<?> action = invocation.getArgument(0);
      return action.execute(redisTemplate);
    });

    given(zSetOperations.zCard(contentKey)).willReturn(1L);

    Long result = watchingSessionService.enterSession(userId, contentId);

    assertEquals(1L, result);
    verify(mockContent).updateWatcherCount(1L);

    //정확한 이벤트 타입과 contentId 검증
    ArgumentCaptor<WatchingSessionEvent> eventCaptor = ArgumentCaptor.forClass(WatchingSessionEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    WatchingSessionEvent publishedEvent = eventCaptor.getValue();
    assertEquals(contentId, publishedEvent.contentId());
    assertEquals("JOIN", publishedEvent.changeEvent().type());
  }

  @Test
  @DisplayName("유저 입장 실패 - 콘텐츠가 없으면 Redis 기록 전 예외 발생")
  void enterSession_Fail_ContentNotFound() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    //콘텐츠가 DB에 없다고 설정
    given(contentRepository.findById(contentId)).willReturn(Optional.empty());

    //Redis 진입 전 터지는지 검증
    assertThrows(ContentException.class, () -> watchingSessionService.enterSession(userId, contentId));

    //예외 발생 시 Redis 트랜잭션 로직이 단 한 번도 호출되지 않았음을 명시적으로 검증
    verify(redisTemplate, org.mockito.Mockito.never()).execute(any(SessionCallback.class));
  }

  @Test
  @DisplayName("유저 퇴장 성공 - Redis 삭제, DB 동기화 및 웹소켓 브로드캐스팅 확인")
  void leaveSession_Success() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String contentKey = "content:watchers:" + contentId;
    String sessionIdKey = "user:session:id:" + userId;
    String userKey = "user:watching:" + userId; //SessionCallback 내부 조회를 위한 userKey

    Content mockContent = mock(Content.class);
    lenient().when(mockContent.getType()).thenReturn(ContentType.MOVIE);

    given(valueOperations.get(sessionIdKey)).willReturn(UUID.randomUUID().toString());

    //익명 클래스 내부 로직을 끝까지 타게 만들기 위한 모킹 설정
    given(valueOperations.get(userKey)).willReturn(contentId.toString());
    given(redisTemplate.exec()).willReturn(List.of(new Object()));

    //강제로 "SUCCESS"를 리턴하지 않고 실제 익명 클래스 내부 로직의 반환값을 사용하도록 변경
    given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
      SessionCallback<?> action = invocation.getArgument(0);
      return action.execute(redisTemplate);
    });

    given(zSetOperations.zCard(contentKey)).willReturn(0L); //퇴장 후 총 0명
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));

    Long result = watchingSessionService.leaveSession(userId, contentId);

    assertEquals(0L, result);
    verify(mockContent).updateWatcherCount(0L); //DB 동기화가 정상 호출되었는지 검증

    //정확한 이벤트 타입과 contentId 검증
    ArgumentCaptor<WatchingSessionEvent> eventCaptor = ArgumentCaptor.forClass(WatchingSessionEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    WatchingSessionEvent publishedEvent = eventCaptor.getValue();
    assertEquals(contentId, publishedEvent.contentId());
    assertEquals("LEAVE", publishedEvent.changeEvent().type());
  }

  @Test
  @DisplayName("특정 유저가 시청 중인 콘텐츠 ID 조회 성공")
  void getWatchingContentId_Success() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String userKey = "user:watching:" + userId;

    User mockUser = mock(User.class);

    given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    given(valueOperations.get(userKey)).willReturn(contentId.toString());

    UUID result = watchingSessionService.getWatchingContentId(userId);

    assertEquals(contentId, result);
  }

  @Test
  @DisplayName("특정 유저가 시청 중인 콘텐츠 ID 조회 시 시청 중인 영상이 없으면 null을 반환")
  void getWatchingContentId_ReturnsNullWhenNotWatching() {
    UUID userId = UUID.randomUUID();
    String userKey = "user:watching:" + userId;
    User mockUser = mock(User.class);

    //유저는 존재
    given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    //Redis에 시청 중인 콘텐츠 기록이 없는 상황(null 반환)
    given(valueOperations.get(userKey)).willReturn(null);

    //예외가 터지지 않고 null이 반환되어야 함
    UUID result = watchingSessionService.getWatchingContentId(userId);
    org.junit.jupiter.api.Assertions.assertNull(result);
  }

  @Test
  @DisplayName("특정 유저 시청 세션 단건 조회 성공 콘텐츠 정보가 채워져 반환")
  void getWatchingSessionForUser_Success() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    long joinTime = Instant.now().toEpochMilli();

    User mockUser = mock(User.class);
    given(mockUser.getId()).willReturn(userId);
    given(mockUser.getName()).willReturn("시청자");

    Content mockContent = mock(Content.class);
    given(mockContent.getId()).willReturn(contentId);
    given(mockContent.getType()).willReturn(ContentType.MOVIE);
    given(mockContent.getTitle()).willReturn("테스트 콘텐츠");

    given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    given(valueOperations.get("user:watching:" + userId)).willReturn(contentId.toString());
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));
    given(valueOperations.get("user:session:id:" + userId)).willReturn(sessionId.toString());
    given(zSetOperations.score("content:watchers:" + contentId, userId.toString()))
        .willReturn((double) joinTime);

    WatchingSessionResponse result = watchingSessionService.getWatchingSessionForUser(userId);

    assertNotNull(result);
    assertEquals(sessionId, result.id());
    assertEquals(Instant.ofEpochMilli(joinTime), result.createdAt());
    assertEquals(userId, result.watcher().userId());
    assertNotNull(result.content());
    assertEquals(contentId, result.content().id());
    assertEquals("테스트 콘텐츠", result.content().title());
  }

  @Test
  @DisplayName("특정 유저 시청 세션 단건 조회 - 시청 중이 아니면 null 반환")
  void getWatchingSessionForUser_ReturnsNullWhenNotWatching() {
    UUID userId = UUID.randomUUID();

    given(userRepository.findById(userId)).willReturn(Optional.of(mock(User.class)));
    given(valueOperations.get("user:watching:" + userId)).willReturn(null);

    org.junit.jupiter.api.Assertions.assertNull(watchingSessionService.getWatchingSessionForUser(userId));
  }

  @Test
  @DisplayName("특정 유저 시청 세션 단건 조회 - 활성 세션 ID가 없으면 null 반환")
  void getWatchingSessionForUser_ReturnsNullWhenSessionIdMissing() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    Content mockContent = mock(Content.class);

    given(userRepository.findById(userId)).willReturn(Optional.of(mock(User.class)));
    given(valueOperations.get("user:watching:" + userId)).willReturn(contentId.toString());
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));
    given(valueOperations.get("user:session:id:" + userId)).willReturn(null);

    org.junit.jupiter.api.Assertions.assertNull(watchingSessionService.getWatchingSessionForUser(userId));
  }

  @Test
  @DisplayName("시청 세션 목록 조회 실패 - limit 범위 초과 검증")
  void getWatchingSessions_Fail_InvalidLimit() {
    UUID contentId = UUID.randomUUID();
    Content mockContent = mock(Content.class);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));

    //limit가 100을 초과할 때 ContentException 발생 검증
    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, null, 101,
            "ASCENDING", "id"));
  }

  @Test
  @DisplayName("유저 퇴장 무시 - 현재 시청 중인 콘텐츠가 아님(Redis 트랜잭션 취소)")
  void leaveSession_Ignored_NotWatching() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String userKey = "user:watching:" + userId;
    String contentKey = "content:watchers:" + contentId;
    String sessionIdKey = "user:session:id:" + userId;

    lenient().when(valueOperations.get(sessionIdKey)).thenReturn(UUID.randomUUID().toString());

    given(valueOperations.get(userKey)).willReturn(null); // 다른 방에 있거나 시청 중이 아님
    given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
      SessionCallback<?> action = invocation.getArgument(0);
      return action.execute(redisTemplate);
    });
    given(zSetOperations.zCard(contentKey)).willReturn(5L); //방에 다른 유저 5명 남음

    Long result = watchingSessionService.leaveSession(userId, contentId);

    //퇴장이 무시되고 현재 남은 인원(5명)이 그대로 반환되어야 함
    assertEquals(5L, result);
  }

  @Test
  @DisplayName("유저 입장 성공 - 다른 방에서 이동해 온 경우 이전 방 퇴장(LEAVE) 후 새 방 입장(JOIN) 순서 확인")
  void enterSession_Success_RoomSwitching() {
    UUID userId = UUID.randomUUID();
    UUID oldContentId = UUID.randomUUID();
    UUID newContentId = UUID.randomUUID();
    String newContentKey = "content:watchers:" + newContentId;
    String oldContentKey = "content:watchers:" + oldContentId;
    String sessionIdKey = "user:session:id:" + userId;
    String userKey = "user:watching:" + userId;

    Content mockOldContent = mock(Content.class);
    Content mockNewContent = mock(Content.class);
    lenient().when(mockOldContent.getType()).thenReturn(ContentType.MOVIE);
    lenient().when(mockNewContent.getType()).thenReturn(ContentType.MOVIE);

    given(valueOperations.get(sessionIdKey)).willReturn(UUID.randomUUID().toString());

    given(valueOperations.get(userKey)).willReturn(oldContentId.toString());
    given(redisTemplate.exec()).willReturn(List.of(new Object()));
    given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
      SessionCallback<?> action = invocation.getArgument(0);
      return action.execute(redisTemplate);
    });

    lenient().when(zSetOperations.zCard(oldContentKey)).thenReturn(0L);
    given(zSetOperations.zCard(newContentKey)).willReturn(1L);

    given(contentRepository.findById(oldContentId)).willReturn(Optional.of(mockOldContent));
    given(contentRepository.findById(newContentId)).willReturn(Optional.of(mockNewContent));

    watchingSessionService.enterSession(userId, newContentId);

    //ArgumentCaptor를 사용하여 2개의 이벤트 순서 및 데이터 정밀 검증
    ArgumentCaptor<WatchingSessionEvent> eventCaptor = ArgumentCaptor.forClass(WatchingSessionEvent.class);
    verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());

    List<WatchingSessionEvent> publishedEvents = eventCaptor.getAllValues();

    //첫 번째 이벤트는 이전 방의 LEAVE 이벤트여야 함
    assertEquals(oldContentId, publishedEvents.get(0).contentId());
    assertEquals("LEAVE", publishedEvents.get(0).changeEvent().type());

    //두 번째 이벤트는 새 방의 JOIN 이벤트여야 함
    assertEquals(newContentId, publishedEvents.get(1).contentId());
    assertEquals("JOIN", publishedEvents.get(1).changeEvent().type());
  }

  @Test
  @DisplayName("실시간 채팅 브로드캐스팅 성공 - 인증된 유저가 메시지 전송 시 올바른 경로로 발행")
  void broadcastChatMessage_Success() {
    String contentIdStr = UUID.randomUUID().toString();
    ContentChatSendRequest request = new ContentChatSendRequest("안녕하세요!");

    UUID userId = UUID.randomUUID();
    CustomUserDetails userDetails = new CustomUserDetails(userId, Role.USER);
    Principal principal = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    User mockUser = mock(User.class);
    given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    given(mockUser.getName()).willReturn("테스터");

    watchingSessionService.broadcastChatMessage(contentIdStr, request, principal);

    //올바른 채팅 구독 경로로 메시지가 전송되었는지 검증
    verify(messagingTemplate).convertAndSend(
        eq("/sub/contents/" + contentIdStr + "/chat"), any(ContentChatDto.class));
  }

  @Test
  @DisplayName("유저 입장 성공 - 기존 세션 UUID 재사용 및 이벤트 발행 확인")
  void enterSession_Success_ReusesSessionIdAndSendsSse() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    String contentKey = "content:watchers:" + contentId;
    String sessionIdKey = "user:session:id:" + userId;
    String userKey = "user:watching:" + userId;
    String existingSessionUuid = UUID.randomUUID().toString();

    Content mockContent = mock(Content.class);
    lenient().when(mockContent.getType()).thenReturn(ContentType.MOVIE);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));

    //기존 세션 ID가 Redis에 존재하는 상황 모킹
    given(valueOperations.get(sessionIdKey)).willReturn(existingSessionUuid);

    given(valueOperations.get(userKey)).willReturn(null);
    given(redisTemplate.exec()).willReturn(List.of(new Object()));
    given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
      SessionCallback<?> action = invocation.getArgument(0);
      return action.execute(redisTemplate);
    });

    given(zSetOperations.zCard(contentKey)).willReturn(2L);

    watchingSessionService.enterSession(userId, contentId);

    ArgumentCaptor<WatchingSessionEvent> captor = ArgumentCaptor.forClass(WatchingSessionEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    WatchingSessionEvent event = captor.getValue();
    assertEquals("JOIN", event.changeEvent().type());
    assertEquals(existingSessionUuid, event.changeEvent().watchingSession().id().toString());
  }

  @Test
  @DisplayName("실시간 채팅 브로드캐스팅 무시 - 메시지가 비어있거나 Principal이 null일 때 방어 로직 작동")
  void broadcastChatMessage_Ignored_EmptyMessageOrNullPrincipal() {
    String contentIdStr = UUID.randomUUID().toString();

    //Principal(인증 객체)이 null인 경우
    watchingSessionService.broadcastChatMessage(contentIdStr, new ContentChatSendRequest("안녕"), null);

    //메시지가 비어있는 경우(Principal은 정상)
    Principal mockPrincipal = mock(Principal.class);
    watchingSessionService.broadcastChatMessage(contentIdStr, new ContentChatSendRequest(""), mockPrincipal);
    watchingSessionService.broadcastChatMessage(contentIdStr, new ContentChatSendRequest(null), mockPrincipal);

    //3번의 호출 모두 내부 로직에서 early return 되어 convertAndSend가 단 한 번도 호출되지 않아야 함
    verify(messagingTemplate, org.mockito.Mockito.never())
        .convertAndSend(any(String.class), any(ContentChatDto.class));
  }
}