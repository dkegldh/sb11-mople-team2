package com.codeit.mople.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.content.dto.ContentCreateRequest;
import com.codeit.mople.domain.content.dto.ContentPageResponse;
import com.codeit.mople.domain.content.dto.ContentResponse;
import com.codeit.mople.domain.content.dto.ContentUpdateRequest;
import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.service.ContentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContentController.class)
@AutoConfigureMockMvc(addFilters = false) //403 에러 방지를 위해 보안 필터 비활성화
@WithMockUser //가짜 인증 사용자 설정
public class ContentControllerTest {

  @Autowired
  public MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private ContentService contentService;

  //=========================================================================================
  //콘텐츠 생성 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 생성 성공 - 201 Created")
  void createContent_Success() throws Exception {
    UUID adminId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

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

    given(contentService.createContent(any(), any(), any())).willReturn(mockResponse);

    mockMvc.perform(
            multipart(HttpMethod.POST, "/api/contents")
                .file(requestPart)
                .file(thumbnailPart)
                //TODO: 추후 JWT 도입 및 관리자 권한 검증 적용 후 주석 제거 예정
                //.header("X-User-Id", adminId.toString())
                .contentType(MediaType.MULTIPART_FORM_DATA)
        ).andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("테스트 영화"))
        .andExpect(jsonPath("$.type").value("MOVIE"))
        .andExpect(jsonPath("$.averageRating").value(0.0));
  }

  @Test
  @DisplayName("콘텐츠 생성 실패 - 필수 값(제목) 누락 시 400 Bad Request")
  void createContent_Fail_Validation() throws Exception {
    UUID adminId = UUID.randomUUID();
    //title을 빈 문자열("")로 설정하여 @NotBlank 검증 실패
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
                //TODO: 추후 JWT 도입 및 관리자 권한 검증 적용 후 주석 제거 예정
                //.header("X-User-Id", adminId.toString())
                .contentType(MediaType.MULTIPART_FORM_DATA)
        )
        .andExpect(status().isBadRequest());
  }

  //TODO: 추후 JWT 도입 및 관리자 권한 검증 적용 후 주석 해제 후 테스트 복구 예정
  /*
  @Test
  @DisplayName("콘텐츠 생성 실패 - 필수 헤더(X-User-Id) 누락 시 500 Internal Server Error 반환")
  void createContent_Fail_MissingHeader() throws Exception {
    ContentCreateRequest requestDto = new ContentCreateRequest(
        "MOVIE", "테스트 영화", "설명", List.of("액션"));

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
                // .header("X-User-Id", adminId.toString()) 헤더를 의도적으로 누락
                .contentType(MediaType.MULTIPART_FORM_DATA)
        )
        .andExpect(status().isInternalServerError());
  }*/

  //=========================================================================================
  //콘텐츠 목록 조회 테스트
  //TODO: 추후 커서 페이지네이션 적용 예정
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 목록 조회 성공 - 200 OK")
  void getContents_Success() throws Exception {
    ContentResponse content1 = new ContentResponse(
        UUID.randomUUID(), "MOVIE", "테스트 영화1", "설명 1",
        "http://example.com/test1.png", List.of("액션"),
        0.0, 0, 0L);

    ContentResponse content2 = new ContentResponse(
        UUID.randomUUID(), "DRAMA", "테스트 영화2", "설명 2",
        "http://example.com/test2.png", List.of("로맨스"),
        0.0, 0, 0L);

    ContentPageResponse mockPageResponse = new ContentPageResponse(
        List.of(content1, content2),
        null, //당장 커서를 사용 안 하므로 null
        null,
        false,
        2L,
        "createdAt",
        "ASCENDING"
    );

    given(contentService.getContents(anyInt(), anyInt(), any(), any())).willReturn(mockPageResponse);

    mockMvc.perform(
            get("/api/contents")
                .param("limit", "10")
                .param("sortDirection", "ASCENDING")
                .param("sortBy", "createdAt")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].title").value("테스트 영화1"))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  @DisplayName("콘텐츠 목록 조회 성공 - 파라미터 누락 시 기본값이 적용되어 200 OK")
  void getContents_Success_WithDefaultParams() throws Exception {
    ContentResponse content1 = new ContentResponse(
        UUID.randomUUID(), "MOVIE", "테스트 영화1", "설명 1",
        "http://example.com/test1.png", List.of("액션"),
        0.0, 0, 0L);

    ContentPageResponse mockPageResponse = new ContentPageResponse(
        List.of(content1), null, null, false, 1L, "createdAt", "DESCENDING"
    );

    // 파라미터가 누락되었을 때 기본값(10, DESCENDING, createdAt)이 잘 주입되어 서비스가 호출되는지 모킹
    given(contentService.getContents(0, 10, "DESCENDING", "createdAt")).willReturn(mockPageResponse);

    mockMvc.perform(
            get("/api/contents")
                // 파라미터(param)를 모두 의도적으로 누락
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.sortBy").value("createdAt"));
  }

  //=========================================================================================
  //콘텐츠 단건 조회 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 단건 조회 성공 - 200 OK")
  void getContent_Success() throws Exception {
    UUID contentId = UUID.randomUUID();
    ContentResponse mockResponse = new ContentResponse(
        contentId, "MOVIE", "단건 조회 테스트 영화", "설명"
        , "http://example.com/test.png", List.of("액션"),
        0.0, 0, 0L);

    given(contentService.getContent(any(UUID.class))).willReturn(mockResponse);

    mockMvc.perform(
        get("/api/contents/{contentId}", contentId)
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(contentId.toString()))
        .andExpect(jsonPath("$.title").value("단건 조회 테스트 영화"));
  }

  @Test
  @DisplayName("콘텐츠 단건 조회 실패 - 존재하지 않는 ID 조회 시 404 Not Found")
  void getContent_Fail_NotFound() throws Exception {
    UUID contentId = UUID.randomUUID();

    given(contentService.getContent(any(UUID.class)))
        .willThrow(new ContentException(ContentErrorCode.CONTENT_NOT_FOUND,
            Map.of("contentId", contentId)));

    mockMvc.perform(
        get("/api/contents/{contentId}", contentId)
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isNotFound());
  }

  //=========================================================================================
  //콘텐츠 수정 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 수정 성공 - 200 OK")
  void updateContent_Success() throws Exception {
    UUID contentId = UUID.randomUUID();
    UUID adminid = UUID.randomUUID();

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

    given(contentService.updateContent(any(), any(), any(), any())).willReturn(mockResponse);

    mockMvc.perform(
        multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
            .file(requestPart)
            .file(thumbnailPart)
            //TODO: 추후 JWT 도입 및 관리자 권한 적용 시 주석 해제
            //.header("X-User-Id", adminId, toString())
            .contentType(MediaType.MULTIPART_FORM_DATA)
    ).andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("수정된 영화 제목"))
        .andExpect(jsonPath("$.description").value("수정된 설명"));
  }

  @Test
  @DisplayName("콘텐츠 수정 실패 - 존재하지 않는 ID 수정 시 404 Not Found")
  void updateContent_Fail_NotFound() throws Exception {
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

    given(contentService.updateContent(any(), any(), any(), any())).willThrow(
        new ContentException(ContentErrorCode.CONTENT_NOT_FOUND,
            Map.of("contentId", contentId)));

    mockMvc.perform(
        multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
            .file(requestPart)
            .file(thumbnailPart)
            //TODO: 추후 JWT 도입 및 관리자 권한 적용 시 주석 해제
            //.header("X-User-Id", adminId.toString())
            .contentType(MediaType.MULTIPART_FORM_DATA)
    ).andExpect(status().isNotFound());
  }

  //TODO: 추후 JWT 도입 및 관리자 권한(헤더 등) 검증 적용 시 주석 해제하여 테스트 복구 예정
  /*
  @Test
  @DisplayName("콘텐츠 수정 실패 - 필수 헤더(X-User-Id) 누락 시 500 Internal Server Error 반환")
  void updateContent_Fail_MissingHeader() throws Exception {
    UUID contentId = UUID.randomUUID();
    ContentUpdateRequest requestDto = new ContentUpdateRequest(
        "수정된 영화 제목", "수정된 설명", List.of("스릴러"));

    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(requestDto).getBytes(StandardCharsets.UTF_8));

    MockMultipartFile thumbnailPart = new MockMultipartFile(
        "thumbnail", "updated.png", MediaType.IMAGE_PNG_VALUE,
        "updated image content".getBytes());

    mockMvc.perform(
            multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
                .file(requestPart)
                .file(thumbnailPart)
                //.header("X-User-Id", adminId.toString()) 헤더를 의도적으로 누락
                .contentType(MediaType.MULTIPART_FORM_DATA)
        )
        .andExpect(status().isInternalServerError());
  }
  */

  //=========================================================================================
  //콘텐츠 삭제 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 삭제 성공 - 200 OK")
  void deleteContent_Success() throws Exception {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    willDoNothing().given(contentService).deleteContent(any(), any());

    mockMvc.perform(
        delete("/api/contents/{contentId}", contentId)
            //TODO: 추후 JWT 도입 및 관리자 권한 적용 시 주석 해제
            //.header("X-User-Id", adminId.toString())
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk());
  }

  @Test
  @DisplayName("콘텐츠 삭제 실패 - 존재하지 않는 ID 삭제 시 404 Not Found")
  void deleteContent_Fail_NotFound() throws Exception {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    willThrow(new ContentException(ContentErrorCode.CONTENT_NOT_FOUND,
        Map.of("contentId", contentId))).given(contentService).deleteContent(any(), any());

    mockMvc.perform(
        delete("/api/contents/{contentId}", contentId)
            //TODO: 추후 JWT 도입 및 관리자 권한 적용 시 주석 해제
            //.header("X-User-Id", adminId.toString())
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isNotFound());
  }
}
