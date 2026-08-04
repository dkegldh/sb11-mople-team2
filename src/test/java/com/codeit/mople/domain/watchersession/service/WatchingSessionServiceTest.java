package com.codeit.mople.domain.watchersession.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.watchingsession.dto.CursorResponseWatchingSessionDto;
import com.codeit.mople.domain.watchingsession.entity.WatchingSession;
import com.codeit.mople.domain.watchingsession.repository.WatchingSessionQueryRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.watchingsession.service.WatchingSessionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WatchingSessionServiceTest {

  @InjectMocks
  private WatchingSessionService watchingSessionService;

  @Mock
  private WatchingSessionQueryRepository watchingSessionQueryRepository;

  @Mock
  private ContentRepository contentRepository; //NPE 방지용 객체

  //404 예외 로직 검증 테스트
  @Test
  @DisplayName("시청 세션 목록 조회 실패 - 콘텐츠가 존재하지 않음 (404 예외 발생)")
  void getWatchingSessions_Fail_ContentNotFound() {
    UUID contentId = UUID.randomUUID();

    given(contentRepository.existsById(any())).willReturn(false);

    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, null, 10,
            "ASCENDING", "createdAt"));
  }

  @Test
  @DisplayName("시청 세션 목록 조회 성공 - 빈 목록 반환")
  void getWatchingSessions_Success() {
    UUID contentId = UUID.randomUUID();

    given(contentRepository.existsById(any())).willReturn(true);

    given(watchingSessionQueryRepository.findSessionByCursor(
        any(), any(), any(), any(), anyInt(), any(), any()
    )).willReturn(List.of());

    given(watchingSessionQueryRepository.countSessions(any(), any()))
        .willReturn(0L);

    CursorResponseWatchingSessionDto result = watchingSessionService.getWatchingSessions(
        contentId, null, null, null, 10, "ASCENDING", "createdAt"
    );

    assertNotNull(result);
    assertEquals(0L, result.totalCount());
    assertFalse(result.hasNext());
    assertNull(result.nextCursor());
  }

  @Test
  @DisplayName("시청 세션 목록 조회 성공 - 데이터가 있을 때 DTO 매핑 및 nextCursor 추출 검증")
  void getWatchingSessions_Success_WithData() {
    UUID contentId = UUID.randomUUID();
    WatchingSession mockSession = mock(WatchingSession.class);
    User mockUser = mock(User.class);
    Content mockContent = mock(Content.class);

    given(contentRepository.existsById(any())).willReturn(true);

    given(mockSession.getId()).willReturn(UUID.randomUUID());
    given(mockSession.getCreatedAt()).willReturn(Instant.now());
    given(mockSession.getUser()).willReturn(mockUser);
    given(mockSession.getContent()).willReturn(mockContent);

    given(mockUser.getId()).willReturn(UUID.randomUUID());
    given(mockUser.getName()).willReturn("테스터");
    given(mockUser.getProfileImageUrl()).willReturn("url");

    given(mockContent.getId()).willReturn(contentId);
    given(mockContent.getType()).willReturn(ContentType.MOVIE);

    //limit 1 요청에 데이터 2개가 반환되었다고 가정(hasNext = true)
    given(watchingSessionQueryRepository.findSessionByCursor(
        any(), any(), any(), any(), anyInt(), any(), any()
    )).willReturn(List.of(mockSession, mockSession));

    given(watchingSessionQueryRepository.countSessions(any(), any())).willReturn(2L);

    CursorResponseWatchingSessionDto result = watchingSessionService.getWatchingSessions(
        contentId, null, null, null, 1,
        "DESCENDING", "createdAt");

    assertTrue(result.hasNext());
    assertEquals(1, result.data().size()); //subList로 1개만 잘리는지 확인
    assertNotNull(result.nextCursor());
    assertNotNull(result.nextIdAfter());
  }

  @Test
  @DisplayName("시청 세션 목록 조회 실패 - limit이 범위를 벗어날 경우 예외 발생")
  void getWatchingSessions_Fail_LimitTooSmall() {
    UUID contentId = UUID.randomUUID();

    given(contentRepository.existsById(any())).willReturn(true);

    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, null, 0,
            "ASCENDING", "createdAt"));
    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, null, 101,
            "ASCENDING", "createdAt"));
  }

  @Test
  @DisplayName("시청 세션 목록 조회 실패 - 잘못된 정렬 기준 또는 정렬 방향")
  void getWatchingSessions_Fail_InvalidSortParams() {
    UUID contentId = UUID.randomUUID();

    given(contentRepository.existsById(any())).willReturn(true);

    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, null, 10,
            "ASCENDING", "invalidSort"));
    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, null, 10,
            "INVALID_DIR", "createdAt"));
  }

  @Test
  @DisplayName("시청 세션 목록 조회 실패 - 커서 파라미터가 불완전하게 들어온 경우")
  void getWatchingSessions_Fail_IncompleteCursor() {
    UUID contentId = UUID.randomUUID();
    String validDate = Instant.now().toString();

    given(contentRepository.existsById(any())).willReturn(true);

    //cursor만 있고 idAfter가 없는 경우
    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            validDate, null, 10,
            "ASCENDING", "createdAt"));
    //idAfter만 있고 cursor가 없는 경우
    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            null, UUID.randomUUID(), 10,
            "ASCENDING", "createdAt"));
  }

  @Test
  @DisplayName("시청 세션 목록 조회 실패 - cursor 날짜 포맷이 올바르지 않은 경우")
  void getWatchingSessions_Fail_InvalidDateFormat() {
    UUID contentId = UUID.randomUUID();

    given(contentRepository.existsById(any())).willReturn(true);

    assertThrows(ContentException.class, () ->
        watchingSessionService.getWatchingSessions(contentId, null,
            "invalid-date-format", UUID.randomUUID(), 10,
            "ASCENDING", "createdAt"));
  }
}
