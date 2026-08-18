package com.codeit.mople.domain.watchingsession.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionChange;
import com.codeit.mople.global.sse.service.SseService;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
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
  private SimpMessagingTemplate messagingTemplate;

  @Mock
  private ValueOperations<String, Object> valueOperations;

  @Mock
  private SetOperations<String, Object> setOperations;

  @Mock
  private SseService sseService;

  @BeforeEach
  void setUp() {
    // RedisTemplate 의 opsForValue, opsForSet 호출 시 Mock 객체 반환하도록 설정
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
  }

  @Test
  @DisplayName("시청 세션 목록 조회 실패 - 콘텐츠가 존재하지 않음(404 예외 발생)")
  void getWatchingSessions_Fail_ContentNotFound() {
    UUID contentId = UUID.randomUUID();

    given(contentRepository.findById(contentId)).willReturn(Optional.empty());

    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, null, 10,
            "ASCENDING", "id"));
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

    //Redis에 1명의 유저가 접속 중이라고 가정
    given(setOperations.members(contentKey)).willReturn(Set.of(userId.toString()));

    //DB에서 해당 유저 정보 조회 모킹
    given(userRepository.findAllById(anyList())).willReturn(List.of(mockUser));
    given(mockUser.getId()).willReturn(userId);
    given(mockContent.getType()).willReturn(ContentType.MOVIE);
    given(mockUser.getName()).willReturn("테스터");

    CursorResponseWatchingSessionDto result = watchingSessionService.getWatchingSessions(
        contentId, null, null, null, 10,
        "DESCENDING", "id");

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
    given(setOperations.members(contentKey)).willReturn(Set.of(user1Id.toString(), user2Id.toString()));

    given(userRepository.findAllById(any())).willReturn(List.of(user1, user2));

    given(user1.getId()).willReturn(user1Id);
    given(user2.getId()).willReturn(user2Id);

    given(user1.getName()).willReturn("홍길동");
    given(user2.getName()).willReturn("김철수");
    given(mockContent.getType()).willReturn(ContentType.MOVIE);

    CursorResponseWatchingSessionDto result = watchingSessionService.getWatchingSessions(
        contentId, "홍길", null, null, 10,
        "DESCENDING", "id");

    assertNotNull(result);
    assertEquals(1, result.totalCount()); // "홍길동" 1명만 필터링되어야 함
    assertEquals("홍길동", result.data().get(0).watcher().name());
  }

  @Test
  @DisplayName("유저 입장 성공 - Redis 기록, DB 동기화 및 웹소켓 브로드캐스팅 확인")
  void enterSession_Success() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String userKey = "user:watching:" + userId;
    String contentKey = "content:watchers:" + contentId;
    String sessionIdKey = "user:session:id:" + userId;

    Content mockContent = mock(Content.class);
    lenient().when(mockContent.getType()).thenReturn(ContentType.MOVIE);

    given(valueOperations.get(sessionIdKey)).willReturn(null);

    given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
      SessionCallback<?> action = invocation.getArgument(0);
      action.execute(redisTemplate);
      return "NULL_PREV";
    });

    given(valueOperations.get(userKey)).willReturn(null);
    given(setOperations.size(contentKey)).willReturn(1L); //입장 후 총 1명
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));

    Long result = watchingSessionService.enterSession(userId, contentId);

    assertEquals(1L, result);
    verify(mockContent).updateWatcherCount(1L); //DB 동기화가 정상 호출되었는지 검증
    verify(messagingTemplate).convertAndSend(eq(
        "/sub/contents/" + contentId + "/watch"), any(WatchingSessionChange.class));
  }

  @Test
  @DisplayName("유저 퇴장 성공 - Redis 삭제, DB 동기화 및 웹소켓 브로드캐스팅 확인")
  void leaveSession_Success() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String userKey = "user:watching:" + userId;
    String contentKey = "content:watchers:" + contentId;
    String sessionIdKey = "user:session:id:" + userId;

    Content mockContent = mock(Content.class);
    lenient().when(mockContent.getType()).thenReturn(ContentType.MOVIE);

    given(valueOperations.get(sessionIdKey)).willReturn(UUID.randomUUID().toString());

    given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
      SessionCallback<?> action = invocation.getArgument(0);
      action.execute(redisTemplate);
      return "SUCCESS";
    });

    given(valueOperations.get(userKey)).willReturn(contentId.toString());
    given(setOperations.size(contentKey)).willReturn(0L); //퇴장 후 총 0명
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));

    Long result = watchingSessionService.leaveSession(userId, contentId);

    assertEquals(0L, result);
    verify(mockContent).updateWatcherCount(0L); //DB 동기화가 정상 호출되었는지 검증
    verify(messagingTemplate).convertAndSend(eq(
        "/sub/contents/" + contentId + "/watch"), any(WatchingSessionChange.class));
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

    given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
      SessionCallback<?> action = invocation.getArgument(0);
      action.execute(redisTemplate);
      return "NOT_WATCHING";
    });

    given(valueOperations.get(userKey)).willReturn(null);
    given(setOperations.size(contentKey)).willReturn(5L); //방에 다른 유저 5명 남음

    Long result = watchingSessionService.leaveSession(userId, contentId);

    //퇴장이 무시되고 현재 남은 인원(5명)이 그대로 반환되어야 함
    assertEquals(5L, result);
  }

  @Test
  @DisplayName("유저 입장 성공 - 다른 방에서 이동해 온 경우 이전 방 퇴장(LEAVE) 이벤트 브로드캐스팅 확인")
  void enterSession_Success_RoomSwitching() {
    UUID userId = UUID.randomUUID();
    UUID oldContentId = UUID.randomUUID();
    UUID newContentId = UUID.randomUUID();
    String userKey = "user:watching:" + userId;
    String newContentKey = "content:watchers:" + newContentId;
    String oldContentKey = "content:watchers:" + oldContentId;
    String sessionIdKey = "user:session:id:" + userId;

    Content mockOldContent = mock(Content.class);
    Content mockNewContent = mock(Content.class);
    lenient().when(mockOldContent.getType()).thenReturn(ContentType.MOVIE);
    lenient().when(mockNewContent.getType()).thenReturn(ContentType.MOVIE);

    given(valueOperations.get(sessionIdKey)).willReturn(UUID.randomUUID().toString());

    //Redis 트랜잭션 실행 시 이전 방 ID("oldContentId")를 반환하도록 설정
    given(redisTemplate.execute(any(SessionCallback.class))).willReturn(oldContentId.toString());

    //lenient()를 적용하거나 실제 로직에서 타는 size()만 지정
    lenient().when(setOperations.size(oldContentKey)).thenReturn(0L);
    given(setOperations.size(newContentKey)).willReturn(1L);

    given(contentRepository.findById(oldContentId)).willReturn(Optional.of(mockOldContent));
    given(contentRepository.findById(newContentId)).willReturn(Optional.of(mockNewContent));

    watchingSessionService.enterSession(userId, newContentId);

    //이전 방에 대한 LEAVE 메시지가 브로드캐스팅 되었는지 검증
    verify(messagingTemplate).convertAndSend(eq(
        "/sub/contents/" + oldContentId + "/watch"), any(WatchingSessionChange.class));
    //새 방에 대한 ENTER 메시지가 브로드캐스팅 되었는지 검증
    verify(messagingTemplate).convertAndSend(eq(
        "/sub/contents/" + newContentId + "/watch"), any(WatchingSessionChange.class));
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
  @DisplayName("유저 입장 성공 - 기존 세션 UUID 재사용 및 SSE 브로드캐스팅 확인")
  void enterSession_Success_ReusesSessionIdAndSendsSse() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    //userKey는 해당 테스트에서 호출되지 않으므로 제거 가능
    String contentKey = "content:watchers:" + contentId;
    String sessionIdKey = "user:session:id:" + userId;
    String existingSessionUuid = UUID.randomUUID().toString();

    Content mockContent = mock(Content.class);
    lenient().when(mockContent.getType()).thenReturn(ContentType.MOVIE);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(mockContent));

    //기존 세션 ID가 Redis에 존재하는 상황 모킹
    given(valueOperations.get(sessionIdKey)).willReturn(existingSessionUuid);

    given(redisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> "NULL_PREV");

    given(setOperations.size(contentKey)).willReturn(2L); //방 인원 2명 가정

    //SSE 발송 대상자 모킹(방에 본인 포함 2명이 있다고 가정)
    UUID otherUserId = UUID.randomUUID();
    given(setOperations.members(contentKey)).willReturn(Set.of(userId.toString(), otherUserId.toString()));

    watchingSessionService.enterSession(userId, contentId);

    //웹소켓 이벤트 캡처 및 검증
    org.mockito.ArgumentCaptor<WatchingSessionChange> captor = org.mockito.ArgumentCaptor.forClass(WatchingSessionChange.class);
    verify(messagingTemplate).convertAndSend(eq("/sub/contents/" + contentId + "/watch"), captor.capture());

    WatchingSessionChange event = captor.getValue();
    assertEquals("JOIN", event.type());
    assertEquals(existingSessionUuid, event.watchingSession().id().toString());

    //SSE 전송 검증(본인 및 다른 유저 모두에게 전송되어야 함)
    verify(sseService).send(eq(userId), eq("watch"), org.mockito.ArgumentMatchers.same(event));
    verify(sseService).send(eq(otherUserId), eq("watch"), org.mockito.ArgumentMatchers.same(event));
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