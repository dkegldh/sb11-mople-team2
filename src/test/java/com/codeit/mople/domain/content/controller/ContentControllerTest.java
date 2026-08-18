package com.codeit.mople.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomOAuth2UserService;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.auth.security.handler.OAuth2FailureHandler;
import com.codeit.mople.domain.auth.security.handler.OAuth2SuccessHandler;
import com.codeit.mople.domain.content.dto.ContentCreateRequest;
import com.codeit.mople.domain.content.dto.ContentResponse;
import com.codeit.mople.domain.content.dto.ContentUpdateRequest;
import com.codeit.mople.domain.content.dto.CursorResponseContentDto;
import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.service.ContentService;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.domain.watchingsession.service.WatchingSessionService;
import com.codeit.mople.global.config.SecurityConfig;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ContentController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
public class ContentControllerTest {

  @Autowired
  public MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private ContentService contentService;

  @MockitoBean
  private JwtProvider jwtProvider;

  @MockitoBean
  private UserRepository userRepository;

  @MockitoBean
  private WatchingSessionService watchingSessionService;

  @MockitoBean
  private CustomOAuth2UserService customOAuth2UserService;

  @MockitoBean
  private OAuth2SuccessHandler oAuth2SuccessHandler;

  @MockitoBean
  private OAuth2FailureHandler oAuth2FailureHandler;

  @MockitoBean
  private SessionTokenRepository sessionTokenRepository;

  private RequestPostProcessor mockAuth(UUID userId, Role role) {
    CustomUserDetails mockUser = new CustomUserDetails(userId, role);
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(mockUser, null, mockUser.getAuthorities());
    return authentication(authentication);
  }

  //=========================================================================================
  //콘텐츠 생성 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 생성 성공 - ADMIN 권한일 때 201 Created")
  void createContent_Success() throws Exception {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    ContentCreateRequest requestDto = new ContentCreateRequest(
        "MOVIE", "테스트 영화", "설명", List.of("액션"));

    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(requestDto).getBytes(StandardCharsets.UTF_8));
    MockMultipartFile thumbnailPart = new MockMultipartFile(
        "thumbnail", "test.png", MediaType.IMAGE_PNG_VALUE,
        "dummy image content".getBytes());

    ContentResponse mockResponse = new ContentResponse(
        contentId, "MOVIE", "테스트 영화", "설명",
        "http://example.com/test.png", List.of("액션"), 0.0,
        0, 0L);

    given(contentService.createContent(any(), any())).willReturn(mockResponse);

    mockMvc.perform(
            multipart(HttpMethod.POST, "/api/contents")
                .file(requestPart)
                .file(thumbnailPart)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .with(csrf())
                .with(mockAuth(adminId, Role.ADMIN))
        ).andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("테스트 영화"));
  }

  @Test
  @DisplayName("콘텐츠 생성 실패 - 인증 없으면 401 Unauthorized")
  void createContent_Fail_Unauthorized() throws Exception {
    ContentCreateRequest requestDto = new ContentCreateRequest(
        "MOVIE", "테스트 영화", "설명", List.of("액션"));
    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(requestDto).getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(
        multipart(HttpMethod.POST, "/api/contents")
            .file(requestPart)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .with(csrf())
        // mockAuth를 주입하지 않음 (익명 사용자)
    ).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("콘텐츠 생성 실패 - 필수 값(제목) 누락 시 400 Bad Request")
  void createContent_Fail_Validation() throws Exception {
    UUID adminId = UUID.randomUUID();
    ContentCreateRequest requestDto = new ContentCreateRequest(
        "MOVIE", "", "설명", List.of("액션"));
    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(requestDto).getBytes(StandardCharsets.UTF_8));
    MockMultipartFile thumbnailPart = new MockMultipartFile(
        "thumbnail", "test.png", MediaType.IMAGE_PNG_VALUE,
        "dummy image content".getBytes());

    mockMvc.perform(
            multipart(HttpMethod.POST, "/api/contents")
                .file(requestPart)
                .file(thumbnailPart)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .with(csrf())
                .with(mockAuth(adminId, Role.ADMIN))
        )
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("콘텐츠 생성 실패 - USER 권한일 때 403 Forbidden")
  void createContent_Fail_Forbidden() throws Exception {
    UUID userId = UUID.randomUUID();
    ContentCreateRequest requestDto = new ContentCreateRequest("MOVIE", "테스트 영화", "설명",
        List.of("액션"));
    MockMultipartFile requestPart = new MockMultipartFile("request", "",
        MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(requestDto).getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(multipart(HttpMethod.POST, "/api/contents")
            .file(requestPart)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .with(csrf())
            .with(mockAuth(userId, Role.USER)))
        .andExpect(status().isForbidden());
  }

  //=========================================================================================
  //콘텐츠 목록 조회 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 목록 조회 성공 - 200 OK")
  void getContents_Success() throws Exception {
    ContentResponse content1 = new ContentResponse(
        UUID.randomUUID(), "MOVIE", "테스트 영화1", "설명 1",
        "http://example.com/test1.png", List.of("액션"),
        0.0, 0, 0L);
    ContentResponse content2 = new ContentResponse(
        UUID.randomUUID(), "tvSeries", "테스트 영화2", "설명 2",
        "http://example.com/test2.png", List.of("로맨스"),
        0.0, 0, 0L);

    CursorResponseContentDto mockPageResponse = new CursorResponseContentDto(
        List.of(content1, content2), "next-cursor-string", UUID.randomUUID(),
        false, 2L, "createdAt", "DESCENDING");

    given(contentService.getContents(any(), any(), anyInt(), any(), any(), any())).willReturn(mockPageResponse);

    mockMvc.perform(
        get("/api/contents")
            .param("limit", "20")
            .param("idAfter", UUID.randomUUID().toString())
            .param("cursor", Instant.now().toString())
            .param("keywordLike", "테스트")
            .contentType(MediaType.APPLICATION_JSON)
            .with(mockAuth(UUID.randomUUID(),Role.USER))
    ).andExpect(status().isOk());
  }

  @Test
  @DisplayName("콘텐츠 목록 조회 성공 - 커서 파라미터 누락 시 기본값이 적용되어 200 OK (첫 페이지)")
  void getContents_Success_WithDefaultParams() throws Exception {
    ContentResponse content1 = new ContentResponse(
        UUID.randomUUID(), "MOVIE", "테스트 영화1", "설명 1",
        "http://example.com/test1.png", List.of("액션"),
        0.0, 0, 0L);

    CursorResponseContentDto mockPageResponse = new CursorResponseContentDto(
        List.of(content1), null, null, false, 1L, "createdAt", "DESCENDING");

    given(contentService.getContents(null, null, 20, null, null, "createdAt")).willReturn(mockPageResponse);

    mockMvc.perform(
        get("/api/contents")
            .contentType(MediaType.APPLICATION_JSON)
            .with(mockAuth(UUID.randomUUID(),Role.USER))
    ).andExpect(status().isOk());
  }

  @Test
  @DisplayName("콘텐츠 목록 조회 성공 - type 파라미터 누락 시 contentTypeParam 또는 typeEqual로 대체된다")
  void getContents_Success_ParameterFallback() throws Exception {
    CursorResponseContentDto mockPageResponse = new CursorResponseContentDto(
        List.of(), null, null, false, 0L, "createdAt", "DESCENDING");

    //서비스 단의 actualType(4번째 파라미터)이 "MOVIE"로 치환되어 전달되는지 모킹으로 검증
    given(contentService.getContents(any(), any(), eq(20), eq("MOVIE"), any(), any()))
        .willReturn(mockPageResponse);

    mockMvc.perform(
        get("/api/contents")
            .param("contentType", "MOVIE") //type 파라미터 대신 contentType 파라미터 전달
            .contentType(MediaType.APPLICATION_JSON)
            .with(mockAuth(UUID.randomUUID(), Role.USER))
    ).andExpect(status().isOk());

    //컨트롤러 내부 로직에 의해 치환된 actualType("MOVIE")으로 서비스 메서드가 호출되었는지 확인
    verify(contentService).getContents(any(), any(), eq(20), eq("MOVIE"), any(), any());
  }

  //=========================================================================================
  //콘텐츠 단건 조회 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 단건 조회 성공 - 200 OK")
  void getContent_Success() throws Exception {
    UUID contentId = UUID.randomUUID();
    ContentResponse mockResponse = new ContentResponse(
        contentId, "MOVIE", "단건 조회 테스트 영화", "설명",
        "http://example.com/test.png", List.of("액션"),
        0.0, 0, 0L);

    given(contentService.getContent(any(UUID.class))).willReturn(mockResponse);

    mockMvc.perform(
        get("/api/contents/{contentId}", contentId)
            .contentType(MediaType.APPLICATION_JSON)
            .with(mockAuth(UUID.randomUUID(),Role.USER))
    ).andExpect(status().isOk());
  }

  @Test
  @DisplayName("콘텐츠 단건 조회 실패 - 존재하지 않는 ID 조회 시 404 Not Found")
  void getContent_Fail_NotFound() throws Exception {
    UUID contentId = UUID.randomUUID();
    given(contentService.getContent(any(UUID.class)))
        .willThrow(new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("contentId", contentId)));

    mockMvc.perform(
        get("/api/contents/{contentId}", contentId)
            .contentType(MediaType.APPLICATION_JSON)
            .with(mockAuth(UUID.randomUUID(),Role.USER))
    ).andExpect(status().isNotFound());
  }

  //=========================================================================================
  //콘텐츠 수정 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 수정 성공 - 200 OK")
  void updateContent_Success() throws Exception {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    ContentUpdateRequest requestDto = new ContentUpdateRequest(
        "수정된 영화 제목", "수정된 설명", List.of("스릴러"));
    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(requestDto).getBytes(StandardCharsets.UTF_8));
    MockMultipartFile thumbnailPart = new MockMultipartFile(
        "thumbnail", "updated.png", MediaType.IMAGE_PNG_VALUE,
        "updated image content".getBytes());

    ContentResponse mockResponse = new ContentResponse(
        contentId, "MOVIE", "수정된 영화 제목", "수정된 설명",
        "http://example.com/updated.png", List.of("스릴러"),
        0.0, 0, 0L);

    given(contentService.updateContent(eq(contentId), any(), any())).willReturn(mockResponse);

    mockMvc.perform(
        multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
            .file(requestPart)
            .file(thumbnailPart)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .with(csrf())
            .with(mockAuth(adminId, Role.ADMIN))
    ).andExpect(status().isOk());
  }

  @Test
  @DisplayName("콘텐츠 수정 실패 - 존재하지 않는 ID 수정 시 404 Not Found")
  void updateContent_Fail_NotFound() throws Exception {
    UUID contentId = UUID.randomUUID();

    ContentUpdateRequest requestDto = new ContentUpdateRequest(
        "수정된 영화 제목", "수정된 설명", List.of("스릴러"));
    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(requestDto).getBytes(StandardCharsets.UTF_8));
    MockMultipartFile thumbnailPart = new MockMultipartFile(
        "thumbnail", "updated.png", MediaType.IMAGE_PNG_VALUE,
        "updated image content".getBytes());

    given(contentService.updateContent(eq(contentId), any(), any())).willThrow(
        new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("contentId", contentId)));

    mockMvc.perform(
        multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
            .file(requestPart)
            .file(thumbnailPart)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .with(csrf())
            .with(mockAuth(UUID.randomUUID(), Role.ADMIN))
    ).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("콘텐츠 수정 실패 - 필수 값(제목) 누락 시 400 Bad Request")
  void updateContent_Fail_Validation() throws Exception {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    //제목을 빈 문자열로 세팅한 잘못된 DTO
    ContentUpdateRequest requestDto = new ContentUpdateRequest(
        "", "수정된 설명", List.of("스릴러"));
    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(requestDto).getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(
            multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
                .file(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .with(csrf())
                .with(mockAuth(adminId, Role.ADMIN))
        )
        .andExpect(status().isBadRequest()); //컨트롤러 단에서 400 에러를 반환하는지 검증
  }

  //=========================================================================================
  //콘텐츠 삭제 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 삭제 성공 - 204 OK")
  void deleteContent_Success() throws Exception {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    willDoNothing().given(contentService).deleteContent(eq(contentId));

    mockMvc.perform(
        delete("/api/contents/{contentId}", contentId)
            .contentType(MediaType.APPLICATION_JSON)
            .with(csrf())
            .with(mockAuth(adminId, Role.ADMIN))
    ).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("콘텐츠 삭제 실패 - 존재하지 않는 ID 삭제 시 404 Not Found")
  void deleteContent_Fail_NotFound() throws Exception {
    UUID contentId = UUID.randomUUID();
    willThrow(new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("contentId", contentId)))
        .given(contentService).deleteContent(eq(contentId));

    mockMvc.perform(
        delete("/api/contents/{contentId}", contentId)
            .contentType(MediaType.APPLICATION_JSON)
            .with(csrf())
            .with(mockAuth(UUID.randomUUID(),Role.ADMIN))
    ).andExpect(status().isNotFound());
  }
}