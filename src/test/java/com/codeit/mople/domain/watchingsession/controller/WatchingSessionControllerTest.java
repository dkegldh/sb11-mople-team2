package com.codeit.mople.domain.watchingsession.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.watchingsession.dto.CursorResponseWatchingSessionDto;
import com.codeit.mople.domain.watchingsession.service.WatchingSessionService;
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

  @Test
  @DisplayName("특정 유저 시청 중인 콘텐츠 ID 조회 성공 - 200 OK")
  void getWatchingSessionForUser_Success() throws Exception {
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    given(watchingSessionService.getWatchingContentId(watcherId)).willReturn(contentId);

    mockMvc.perform(get("/api/users/{watcherId}/watching-session", watcherId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contentId").value(contentId.toString()));
  }

  @Test
  @DisplayName("특정 유저 시청 중인 콘텐츠 ID 조회 실패 (시청 중 아님) - 404 Not Found")
  void getWatchingSessionForUser_NotFound() throws Exception {
    UUID watcherId = UUID.randomUUID();

    given(watchingSessionService.getWatchingContentId(watcherId))
        .willThrow(new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("watcherId", watcherId)));

    //단수형 경로로 요청하고 404 상태를 기대
    mockMvc.perform(get("/api/users/{watcherId}/watching-session", watcherId))
        .andExpect(status().isNotFound());
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