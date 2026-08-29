package com.codeit.mople.domain.watchingsession.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.watchingsession.dto.CursorResponseWatchingSessionDto;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionContentDto;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionResponse;
import com.codeit.mople.domain.watchingsession.service.WatchingSessionService;
import com.codeit.mople.global.dto.UserSummary;
import com.codeit.mople.global.error.DiscordWebhookService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WatchingSessionController.class)
@WithMockUser
class WatchingSessionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private WatchingSessionService watchingSessionService;

  @MockitoBean
  private MeterRegistry meterRegistry;

  @MockitoBean
  private DiscordWebhookService discordWebhookService;

  @Test
  @DisplayName("특정 유저 시청 세션 조회 성공 - 200 OK (콘텐츠 정보 포함)")
  void getWatchingSessionForUser_Success() throws Exception {
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();

    WatchingSessionResponse mockResponse = new WatchingSessionResponse(
        sessionId,
        Instant.now(),
        new UserSummary(watcherId, "시청자", null),
        new WatchingSessionContentDto(
            contentId, "MOVIE", "테스트 콘텐츠", "설명", null, List.of(), 0.0, 0L)
    );

    given(watchingSessionService.getWatchingSessionForUser(watcherId)).willReturn(mockResponse);

    mockMvc.perform(get("/api/users/{watcherId}/watching-sessions", watcherId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(sessionId.toString()))
        .andExpect(jsonPath("$.watcher.userId").value(watcherId.toString()))
        .andExpect(jsonPath("$.content.id").value(contentId.toString()))
        .andExpect(jsonPath("$.content.title").value("테스트 콘텐츠"));
  }

  @Test
  @DisplayName("특정 유저 시청 세션 조회 (시청 중 아님) - 200 OK, 본문 없음")
  void getWatchingSessionForUser_EmptyBody() throws Exception {
    UUID watcherId = UUID.randomUUID();

    given(watchingSessionService.getWatchingSessionForUser(watcherId)).willReturn(null);

    mockMvc.perform(get("/api/users/{watcherId}/watching-sessions", watcherId))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  @DisplayName("콘텐츠 실시간 시청자 목록 조회 성공 - 200 OK")
  void getWatchingSessions_Success() throws Exception {
    UUID contentId = UUID.randomUUID();
    CursorResponseWatchingSessionDto mockResponse = new CursorResponseWatchingSessionDto(
        List.of(), null, null, false, 0L, "id", "ASCENDING"
    );

    given(watchingSessionService.getWatchingSessions(
        any(), any(), any(), any(), anyInt(), any(), any()
    )).willReturn(mockResponse);

    mockMvc.perform(get("/api/contents/{contentId}/watching-sessions", contentId)
            .param("limit", "10")
            .param("sortDirection", "ASCENDING")
            .param("sortBy", "id"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(0));
  }

  @Test
  @DisplayName("콘텐츠 실시간 시청자 목록 조회 실패 - 콘텐츠를 찾을 수 없음 (404 Not Found)")
  void getWatchingSessions_Fail_ContentNotFound() throws Exception {
    UUID contentId = UUID.randomUUID();

    given(watchingSessionService.getWatchingSessions(
        any(), any(), any(), any(), anyInt(), any(), any()
    )).willThrow(new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("contentId", contentId)));

    mockMvc.perform(get("/api/contents/{contentId}/watching-sessions", contentId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("콘텐츠 실시간 시청자 목록 조회 실패 - 잘못된 페이징 제한(limit) 요청 (400 Bad Request)")
  void getWatchingSessions_Fail_InvalidPageRequest() throws Exception {
    UUID contentId = UUID.randomUUID();

    given(watchingSessionService.getWatchingSessions(
        any(), any(), any(), any(), anyInt(), any(), any()
    )).willThrow(new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST, Map.of("limit", 0)));

    mockMvc.perform(get("/api/contents/{contentId}/watching-sessions", contentId)
            .param("limit", "0"))
        .andExpect(status().isBadRequest());
  }
}