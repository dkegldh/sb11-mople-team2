package com.codeit.mople.domain.playlist.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.codeit.mople.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.mople.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.repository.PlaylistRepository;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
public class PlaylistIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PlaylistRepository playlistRepository;

  private CustomUserDetails userDetails;
  private User savedOwner;
  private PlaylistCreateRequest createRequest;
  private String title;
  private String description;

  private Playlist playlist;

  private PlaylistUpdateRequest updateRequest;
  private Playlist updatePlaylist;

  @BeforeEach
  void setUp() {
    savedOwner = userRepository.save(
        User.createUser("test@test.com", "12345678", "test")
    );
    userDetails = new CustomUserDetails(savedOwner.getId(), Role.USER);
    title = "새 플레이리스트 (1)";
    description = "새로운 플레이리스트입니다.";
    createRequest = new PlaylistCreateRequest(title, description);

    playlist = Playlist.create(savedOwner, title, description);

    updateRequest = new PlaylistUpdateRequest("수정한 제목", "수정한 설명");
  }

  @Nested
  @DisplayName("플레이리스트 생성")
  class Create {

    @Test
    @DisplayName("플레이리스트 생성 성공")
    void create_success() throws Exception {
      // given

      // BeforeEach에서 DB에 저장된 사용자, PlaylistCreateRequest 초기화

      // when & then
      // 상태 검증
      MvcResult result = mockMvc.perform(post("/api/playlists")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(createRequest)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.owner.userId").value(savedOwner.getId().toString()))
          .andExpect(jsonPath("$.owner.name").value(savedOwner.getName()))
          .andExpect(jsonPath("$.owner.profileImageUrl").value(savedOwner.getProfileImageUrl()))
          .andExpect(jsonPath("$.title").value(title))
          .andExpect(jsonPath("$.description").value(description))
          .andExpect(jsonPath("$.subscriberCount").value(0L))
          .andExpect(jsonPath("$.subscribedByMe").value(false))
          .andExpect(jsonPath("$.contents").isArray())
          .andReturn();

      // DB 검증
      // 응답 추출
      PlaylistResponse response = objectMapper.readValue(
          result.getResponse().getContentAsString(), PlaylistResponse.class
      );

      Playlist playlist = playlistRepository.findById(response.id()).orElseThrow();

      assertThat(playlist.getOwner()).isEqualTo(savedOwner);
      assertThat(playlist.getTitle()).isEqualTo(title);
      assertThat(playlist.getDescription()).isEqualTo(description);
    }

  @Test
  @DisplayName("플레이리스트 생성 실패 - 인증되지 않은 사용자(401 에러)")
  void create_fail_unauthorized() throws Exception {
    // when & then
    mockMvc.perform(post("/api/playlists")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createRequest)))
        .andExpect(status().isUnauthorized());

    assertThat(playlistRepository.count()).isZero();
  }

  }

  @Nested
  @DisplayName("플레이리스트 단건 조회")
  class Find {

    @Test
    @DisplayName("플레이리스트 단건 조회 성공")
    void find_success() throws Exception {
      // given

      // BeforeEach에서 playlist, userDetails를 초기화

      Playlist savedPlaylist = playlistRepository.save(playlist);

      // when & then
      MvcResult result =
          mockMvc.perform(get("/api/playlists/{playlistId}", playlist.getId())
                  .with(user(userDetails))
                  .with(csrf())
              )
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.id").value(savedPlaylist.getId().toString()))
              .andExpect(jsonPath("$.owner.userId").value(savedOwner.getId().toString()))
              .andExpect(jsonPath("$.owner.name").value(savedOwner.getName()))
              .andExpect(jsonPath("$.title").value(title))
              .andExpect(jsonPath("$.description").value(description))
              .andExpect(jsonPath("$.subscriberCount").value(0))
              .andExpect(jsonPath("$.contents").isArray())
              .andReturn();

      // DB 검증
      PlaylistResponse response = objectMapper.readValue(
          result.getResponse().getContentAsString(), PlaylistResponse.class
      );

      Playlist findPlaylist = playlistRepository.findById(response.id()).orElseThrow();

      assertThat(findPlaylist.getOwner()).isEqualTo(savedOwner);
      assertThat(findPlaylist.getTitle()).isEqualTo(title);
      assertThat(findPlaylist.getDescription()).isEqualTo(description);
    }

  }

  @Nested
  @DisplayName("플레이리스트 목록 조회")
  class FindAll {

    @Test
    @DisplayName("플레이리스트 목록 조회 성공")
    void findAll_success() throws Exception {
      // given
      
      // BeforeEach에서 playlist, userDetails를 초기화
      
      // 3개의 Playlist 저장
      playlistRepository.save(playlist);
      playlistRepository.save(
          Playlist.create(
              savedOwner,
              "새 플레이리스트 (2)",
              "새로운 플레이리스트입니다."
          )
      );
      playlistRepository.save(
          Playlist.create(
              savedOwner,
              "새 플레이리스트 (3)",
              "새로운 플레이리스트입니다."
          )
      );

      // when & then
      MvcResult result =
          mockMvc.perform(get("/api/playlists")
                  .param("limit", "2")
                  .param("sortDirection", "ASCENDING")
                  .param("sortBy", "UPDATED_AT")
                  .with(user(userDetails))
                  .with(csrf())
              )
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.data").isArray())
              .andExpect(jsonPath("$.data.length()").value(2))
              .andExpect(jsonPath("$.hasNext").value(true))
              .andExpect(jsonPath("$.totalCount").value(3))
              .andExpect(jsonPath("$.nextCursor").isNotEmpty())
              .andExpect(jsonPath("$.nextIdAfter").isNotEmpty())
              .andReturn();

      // DB 검증
      List<Playlist> findPlaylists = playlistRepository.findAll();

      assertThat(findPlaylists)
          .hasSize(3)
          .extracting(Playlist::getTitle)
          .containsExactlyInAnyOrder(
              title,
              "새 플레이리스트 (2)",
              "새 플레이리스트 (3)"
          );

      assertThat(findPlaylists)
          .allMatch(findPlaylist ->
              findPlaylist.getOwner().equals(savedOwner)
          );
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 실패 - 인증되지 않은 사용자(401 에러)")
    void findAll_fail_unauthorized() throws Exception {
      // when & then
      mockMvc.perform(get("/api/playlists")
              .param("limit", "2")
              .param("sortDirection", "ASCENDING")
              .param("sortBy", "UPDATED_AT")
              .with(csrf())
          )
          .andExpect(status().isUnauthorized());
    }

  }

  @Nested
  @DisplayName("플레이리스트 수정")
  class Update {

    @BeforeEach
    void setUp() {
      updatePlaylist = playlistRepository.save(playlist);
    }

    @Test
    @DisplayName("플레이리스트 수정 성공")
    void update_success() throws Exception {
      // given

      // BeforeEach에서 updateRequest, userDetails 초기화

      // when & then
      mockMvc.perform(patch("/api/playlists/{playlistId}", updatePlaylist.getId())
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(updateRequest))
          )
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(updatePlaylist.getId().toString()))
          .andExpect(jsonPath("$.title").value(updateRequest.title()))
          .andExpect(jsonPath("$.description").value(updateRequest.description()));
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 플레이리스트가 존재하지 않음(404 에러)")
    void update_fail_notFoundPlaylist() throws Exception {
      // given
      UUID notExistPlaylistId = UUID.randomUUID();

      // BeforeEach에서 updateRequest, userDetails 초기화

      // when & then
      mockMvc.perform(patch("/api/playlists/{playlistId}", notExistPlaylistId)
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(updateRequest))
          )
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 인증되지 않은 사용자(401 에러)")
    void update_fail_unauthorized() throws Exception {
      // given

      // BeforeEach에서 updateRequest 초기화

      // when & then
      mockMvc.perform(patch("/api/playlists/{playlistId}", updatePlaylist.getId())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(updateRequest)))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("플레이리스트 삭제")
  class Delete {

    @BeforeEach
    void setUp() {
      updatePlaylist = playlistRepository.save(playlist);
    }

    @Test
    @DisplayName("플레이리스트 삭제 성공")
    void delete_success() throws Exception {
      // given

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(delete("/api/playlists/{playlistId}", updatePlaylist.getId())
              .with(user(userDetails))
              .with(csrf())
          )
          .andExpect(status().isNoContent());

      // DB 검증
      assertThat(playlistRepository.findById(updatePlaylist.getId())).isEmpty();
    }

    @Test
    @DisplayName("플레이리스트 삭제 실패 - 플레이리스트가 존재하지 않음(404 에러)")
    void delete_fail_notFoundPlaylist() throws Exception {
      // given
      UUID notExistPlaylistId = UUID.randomUUID();

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(delete("/api/playlists/{playlistId}", notExistPlaylistId)
              .with(user(userDetails))
              .with(csrf())
          )
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("플레이리스트 삭제 실패 - 인증되지 않은 사용자(401 에러)")
    void delete_fail_unauthorized() throws Exception {
      // when & then
      mockMvc.perform(delete("/api/playlists/{playlistId}", updatePlaylist.getId())
              .with(csrf())
          )
          .andExpect(status().isUnauthorized());
    }
  }
}
