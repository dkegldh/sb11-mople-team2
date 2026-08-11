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

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.domain.watchingsession.dto.CursorResponseWatchingSessionDto;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionChange;
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

    Content mockContent = mock(Content.class);

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

    Content mockContent = mock(Content.class); //DB 동기화 검증용 모의 객체

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
}