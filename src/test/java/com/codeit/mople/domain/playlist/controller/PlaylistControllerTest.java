package com.codeit.mople.domain.playlist.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.PlaylistSortBy;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.SortDirection;
import com.codeit.mople.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.mople.domain.playlist.dto.response.PlaylistCursorResponse;
import com.codeit.mople.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.mople.domain.playlist.exception.PlaylistErrorCode;
import com.codeit.mople.domain.playlist.exception.PlaylistException;
import com.codeit.mople.domain.playlist.service.PlaylistService;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.dto.UserSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaylistController.class)
public class PlaylistControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private PlaylistService playlistService;

  private CustomUserDetails userDetails;
  private User owner;
  private UUID ownerId;
  private PlaylistCreateRequest createRequest;
  private String title;
  private String description;
  private UUID playlistId;
  private PlaylistUpdateRequest updateRequest;

  @BeforeEach
  void setUp() {
    owner = User.createUser("test@test.com", "12345678", "test");
    ownerId = UUID.randomUUID();
    userDetails = new CustomUserDetails(ownerId, Role.USER);
    title = "새 플레이리스트 (1)";
    description = "새로운 플레이리스트입니다.";
    createRequest = new PlaylistCreateRequest(title, description);
    playlistId = UUID.randomUUID();

    updateRequest = new PlaylistUpdateRequest("수정한 제목", "수정한 설명");
  }

  @Nested
  @DisplayName("플레이리스트 생성")
  class Create {

    @Test
    @DisplayName("플레이리스트 생성 성공")
    void create_success() throws Exception {
      // given

      // BeforeEach에서 ownerId, createRequest, title, description, playlistId 초기화

      UserSummary ownerResponse = new UserSummary(
          ownerId,
          "test",
          null
      );

      PlaylistResponse response = new PlaylistResponse(
          playlistId,
          ownerResponse,
          title,
          description,
          Instant.now(),
          0L,
          false,
          List.of()
      );

      given(playlistService.create(any(PlaylistCreateRequest.class), eq(ownerId)))
          .willReturn(response);

      // when & then
      // 결과 중심(상태 검증)
      mockMvc.perform(post("/api/playlists")
              .with(user(userDetails)) // 인증(미호출 시 401 에러)
              .with(csrf()) // 인가(미호출 시 403 에러)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(createRequest))
          )
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(playlistId.toString()))
          .andExpect(jsonPath("$.owner.userId").value(ownerId.toString()))
          .andExpect(jsonPath("$.owner.name").value(owner.getName()))
          .andExpect(jsonPath("$.owner.profileImageUrl").value(owner.getProfileImageUrl()))
          .andExpect(jsonPath("$.title").value(title))
          .andExpect(jsonPath("$.description").value(description))
          .andExpect(jsonPath("$.subscriberCount").value(0L))
          .andExpect(jsonPath("$.subscribedByMe").value(false))
          .andExpect(jsonPath("$.contents").isArray()
          );

      // 행위 중심(PlaylistService.create() 메서드가 호출되었는지 검증)
      verify(playlistService).create(any(PlaylistCreateRequest.class), eq(ownerId));
    }

    @Test
    @DisplayName("플레이리스트 생성 실패 - 제목이 비어있음(400 에러)")
    void create_fail_blankTitle() throws Exception {
      // given
      PlaylistCreateRequest invalidRequest = new PlaylistCreateRequest("", description);

      // BeforeEach에서 ownerId 초기화

      // when & then
      mockMvc.perform(post("/api/playlists")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
          )
          .andExpect(status().isBadRequest());

      // Valid에서 400 에러가 발생하기 때문에 PlaylistService.create() 메서드는 호출되지 않아야함
      verifyNoInteractions(playlistService);
    }

    @Test
    @DisplayName("플레이리스트 생성 실패 - 설명이 비어있음(400 에러)")
    void create_fail_blankDescription() throws Exception {
      // given
      PlaylistCreateRequest invalidRequest = new PlaylistCreateRequest(title, "");

      // BeforeEach에서 ownerId 초기화

      // when & then
      mockMvc.perform(post("/api/playlists")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(playlistService);
    }

  }

  @Nested
  @DisplayName("플레이리스트 단건 조회")
  class Find {

    @Test
    @DisplayName("플레이리스트 단건 조회 성공")
    void find_success() throws Exception {
      // given

      // BeforeEach에서 playlistId, title, description, userDetails 초기화

      UserSummary ownerResponse = new UserSummary(
          ownerId,
          "test",
          null
      );

      PlaylistResponse response = new PlaylistResponse(
          playlistId,
          ownerResponse,
          title,
          description,
          Instant.now(),
          0L,
          false,
          List.of()
      );

      given(playlistService.find(playlistId, userDetails.getUserId()))
          .willReturn(response);

      // when & then
      mockMvc.perform(get("/api/playlists/{playlistId}", playlistId)
              .with(user(userDetails))
          )
          .andExpect(status().isOk());

      verify(playlistService).find(playlistId, userDetails.getUserId());
    }

    @Test
    @DisplayName("플레이리스트 단건 조회 실패 - 플레이리스트가 존재하지 않음(404 에러)")
    void find_fail_notFoundPlaylist() throws Exception {
      // given
      UUID notExistPlaylistId = UUID.randomUUID();

      given(playlistService.find(notExistPlaylistId, userDetails.getUserId()))
          .willThrow(new PlaylistException(
              PlaylistErrorCode.PLAYLIST_NOT_FOUND,
              Map.of("playlistId", notExistPlaylistId)
          ));

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(get("/api/playlists/{playlistId}", notExistPlaylistId)
              .with(user(userDetails))
          )
          .andExpect(status().isNotFound());

      verify(playlistService).find(notExistPlaylistId, userDetails.getUserId());
    }

  }

  @Nested
  @DisplayName("플레이리스트 목록 조회")
  class FindAll {

    @Test
    @DisplayName("플레이리스트 목록 조회 성공")
    void findAll_success() throws Exception {
      // given

      // BeforeEach에서 userDetails 초기화

      PlaylistCursorResponse response = new PlaylistCursorResponse(
          List.of(),
          null,
          null,
          false,
          0L,
          PlaylistSortBy.UPDATED_AT,
          SortDirection.ASCENDING
      );

      given(playlistService.findAll(any(PlaylistQueryCondition.class), eq(userDetails.getUserId())))
          .willReturn(response);

      // when & then
      mockMvc.perform(get("/api/playlists")
              .param("limit", "10")
              .param("sortDirection", "ASCENDING")
              .param("sortBy", "UPDATED_AT")
              .with(user(userDetails))
          )
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.totalCount").value(0));

      verify(playlistService).findAll(
          any(PlaylistQueryCondition.class),
          eq(userDetails.getUserId())
      );
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 실패 - limit 누락(400 에러)")
    void findAll_fail_withoutLimit() throws Exception {
      // given

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(get("/api/playlists")
              .param("sortDirection", "ASCENDING")
              .param("sortBy", "UPDATED_AT")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(playlistService);
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 실패 - 정렬 조건 누락(400 에러)")
    void findAll_fail_withoutSortBy() throws Exception {
      // given

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(get("/api/playlists")
              .param("limit", "10")
              .param("sortDirection", "ASCENDING")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(playlistService);
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 실패 - 정렬 방향 누락(400 에러)")
    void findAll_fail_withoutSortDirection() throws Exception {
      // given

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(get("/api/playlists")
              .param("limit", "10")
              .param("sortBy", "UPDATED_AT")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(playlistService);
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 실패 - limit 값 1 미만(400 에러)")
    void findAll_fail_limitLessThanMin() throws Exception {
      // given

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(get("/api/playlists")
              .param("limit", "0")
              .param("sortDirection", "ASCENDING")
              .param("sortBy", "UPDATED_AT")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(playlistService);
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 실패 - limit 값 100 초과(400 에러)")
    void findAll_fail_limitGreaterThanMax() throws Exception {
      // given

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(get("/api/playlists")
              .param("limit", "1000")
              .param("sortDirection", "ASCENDING")
              .param("sortBy", "UPDATED_AT")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(playlistService);
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 실패 - 잘못된 정렬 조건(400 에러)")
    void findAll_fail_invalidSortBy() throws Exception {
      // given

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(get("/api/playlists")
              .param("limit", "10")
              .param("sortDirection", "ASCENDING")
              .param("sortBy", "ANY")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(playlistService);
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 실패 - 잘못된 정렬 방향(400 에러)")
    void findAll_fail_invalidSortDirection() throws Exception {
      // given

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(get("/api/playlists")
              .param("limit", "10")
              .param("sortDirection", "ANY")
              .param("sortBy", "UPDATED_AT")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(playlistService);
    }

  }

  @Nested
  @DisplayName("플레이리스트 수정")
  class Update {

    @Test
    @DisplayName("플레이리스트 수정 성공")
    void update_success() throws Exception {
      // given

      // BeforeEach에서 playlistId, updateRequest, userDetails 초기화

      UserSummary ownerResponse = new UserSummary(
          ownerId,
          "test",
          null
      );

      PlaylistResponse response = new PlaylistResponse(
          playlistId,
          ownerResponse,
          title,
          description,
          Instant.now(),
          0L,
          false,
          List.of()
      );

      given(playlistService.update(eq(playlistId), any(PlaylistUpdateRequest.class), eq(ownerId)))
          .willReturn(response);

      // when & then
      mockMvc.perform(patch("/api/playlists/{playlistId}", playlistId)
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(updateRequest))
          )
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(playlistId.toString()))
          .andExpect(jsonPath("$.title").value(title))
          .andExpect(jsonPath("$.description").value(description));

      verify(playlistService).update(eq(playlistId), any(PlaylistUpdateRequest.class), eq(ownerId));
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 플레이리스트 소유자가 아님(403 에러)")
    void update_fail_forbidden() throws Exception {
      // given

      // BeforeEach에서 playlistId, updateRequest, userDetails 초기화

      given(playlistService.update(eq(playlistId), any(PlaylistUpdateRequest.class), eq(ownerId)))
          .willThrow(new PlaylistException(
              PlaylistErrorCode.PLAYLIST_FORBIDDEN,
              Map.of("playlistId", playlistId)
          ));

      // when & then
      mockMvc.perform(patch("/api/playlists/{playlistId}", playlistId)
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(updateRequest))
          )
          .andExpect(status().isForbidden());

      verify(playlistService).update(eq(playlistId), any(PlaylistUpdateRequest.class), eq(ownerId));
    }

  }

  @Nested
  @DisplayName("플레이리스트 삭제")
  class Delete {

    @Test
    @DisplayName("플레이리스트 삭제 성공")
    void delete_success() throws Exception {
      // given

      // BeforeEach에서 playlistId, ownerId, userDetails 초기화

      // when & then
      mockMvc.perform(delete("/api/playlists/{playlistId}", playlistId)
              .with(user(userDetails))
              .with(csrf())
          )
          .andExpect(status().isNoContent());

      verify(playlistService).delete(eq(playlistId), eq(ownerId));
    }

    @Test
    @DisplayName("플레이리스트 삭제 실패 - 플레이리스트 소유자가 아님(403 에러)")
    void delete_fail_forbidden() throws Exception {
      // given

      // BeforeEach에서 playlistId, ownerId, userDetails 초기화

      doThrow(new PlaylistException(
          PlaylistErrorCode.PLAYLIST_FORBIDDEN,
          Map.of("playlistId", playlistId)
      ))
          .when(playlistService).delete(eq(playlistId), eq(ownerId));

      // when & then
      mockMvc.perform(delete("/api/playlists/{playlistId}", playlistId)
              .with(user(userDetails))
              .with(csrf())
          )
          .andExpect(status().isForbidden());

      verify(playlistService).delete(eq(playlistId), eq(ownerId));
    }

  }

}
