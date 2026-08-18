package com.codeit.mople.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.content.dto.ContentCreateRequest;
import com.codeit.mople.domain.content.dto.ContentResponse;
import com.codeit.mople.domain.content.dto.ContentUpdateRequest;
import com.codeit.mople.domain.content.dto.CursorResponseContentDto;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentQueryRepository;
import com.codeit.mople.domain.content.repository.ContentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ContentServiceTest {

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentQueryRepository contentQueryRepository;

  @InjectMocks
  private ContentService contentService;

  //=========================================================================================
  //콘텐츠 생성 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 생성 성공 - 레포지토리 저장 및 생성된 객체 직접 반환")
  void createContent_Success() {
    ContentCreateRequest request = new ContentCreateRequest("MOVIE", "테스트 영화",
        "설명", List.of("액션"));
    MockMultipartFile thumbnail = new MockMultipartFile("thumbnail",
        "test.png", "image/png", "dummy".getBytes());

    Content savedContent = new Content(ContentType.MOVIE, "테스트 영화", "설명",
        "/uploads/test.png", List.of("액션"));
    ReflectionTestUtils.setField(savedContent, "id", UUID.randomUUID());

    given(contentRepository.save(any(Content.class))).willReturn(savedContent);

    ContentResponse response = contentService.createContent(request, thumbnail);

    assertThat(response).isNotNull();
    assertThat(response.title()).isEqualTo("테스트 영화");
    verify(contentRepository).save(any(Content.class));
  }

  @Test
  @DisplayName("콘텐츠 생성 실패 - 잘못된 ContentType 전달 시 ContentException 발생")
  void createContent_Fail_InvalidType() {
    UUID adminId = UUID.randomUUID();
    ContentCreateRequest request = new ContentCreateRequest("INVALID_TYPE",
        "테스트", "설명", List.of());

    assertThatThrownBy(() -> contentService.createContent(request, null))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_CONTENT_TYPE);
  }

  @Test
  @DisplayName("콘텐츠 생성 실패 - 이미지 파일이 아닌 형식(txt 등) 업로드 시 INVALID_IMAGE_FILE 예외 발생")
  void createContent_Fail_InvalidImageFile() {
    UUID adminId = UUID.randomUUID();
    ContentCreateRequest request = new ContentCreateRequest("MOVIE", "테스트 영화", "설명", List.of());

    // txt 파일 업로드 시도
    MockMultipartFile invalidFile = new MockMultipartFile("thumbnail",
        "test.txt", "text/plain", "dummy content".getBytes());

    assertThatThrownBy(() -> contentService.createContent(request, invalidFile))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_IMAGE_FILE);
  }

  //=========================================================================================
  //콘텐츠 목록 조회 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 목록 조회 성공 - 커서 없이 조회 시 첫 페이지 데이터 및 CursorResponseContentDto 반환")
  void getContents_Success() {
    int limit = 10;

    Content content1 = new Content(ContentType.MOVIE, "영화1",
        "설명", null, List.of());
    ReflectionTestUtils.setField(content1, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(content1, "createdAt", Instant.now());

    List<Content> mockContents = new ArrayList<>();
    mockContents.add(content1); //limit보다 적게 반환

    given(contentQueryRepository.findContentByCursor(any(), any(), eq(limit), any(), any(), any()))
        .willReturn(mockContents);
    given(contentQueryRepository.countContentsByTypeAndKeyword(any(), any())).willReturn(1L);

    CursorResponseContentDto response = contentService.getContents(
        null, null, limit, null, null, "createdAt");

    assertThat(response).isNotNull();
    assertThat(response.data()).hasSize(1);
    assertThat(response.data().get(0).title()).isEqualTo("영화1");
    assertThat(response.totalCount()).isEqualTo(1L);
    assertThat(response.hasNext()).isFalse(); // limit보다 작으므로 false
  }

  @Test
  @DisplayName("콘텐츠 목록 조회 성공 - type 필터가 ALL이거나 유효하지 않으면 예외 없이 전체 조회를 수행한다")
  void getContents_Success_InvalidTypeFallback() {
    int limit = 10;

    given(contentQueryRepository.findContentByCursor(any(), any(), eq(limit), eq(null), any(), any()))
        .willReturn(new ArrayList<>());
    given(contentQueryRepository.countContentsByTypeAndKeyword(eq(null), any())).willReturn(0L);

    //type 파라미터에 유효하지 않은 "INVALID_TYPE" 전달
    CursorResponseContentDto response = contentService.getContents(
        null, null, limit, "INVALID_TYPE", null, "createdAt");

    assertThat(response).isNotNull();

    //예외가 터지지 않고 contentType이 null(전체 조회)로 치환되어 쿼리 메서드가 호출되었는지 검증
    verify(contentQueryRepository).findContentByCursor(any(), any(), eq(limit), eq(null), any(), any());
  }

  @Test
  @DisplayName("콘텐츠 목록 조회 실패 - limit 값이 0 이하일 경우 ContentException 발생")
  void getContents_Fail_NegativeLimit() {
    assertThatThrownBy(() -> contentService.getContents(
        null, null, -1, null, null, null))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_PAGE_REQUEST);
  }

  //=========================================================================================
  //콘텐츠 단건 조회 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 단건 조회 성공 - 존재하는 ID로 조회 시 정상 반환")
  void getContent_Success() {
    UUID contentId = UUID.randomUUID();
    Content content = new Content(ContentType.MOVIE, "단건 조회 영화", "설명", null, List.of());
    ReflectionTestUtils.setField(content, "id", contentId);

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.of(content));

    ContentResponse response = contentService.getContent(contentId);

    assertThat(response).isNotNull();
    assertThat(response.title()).isEqualTo("단건 조회 영화");
    verify(contentRepository).findById(contentId);
  }

  @Test
  @DisplayName("콘텐츠 단건 조회 실패 - 존재하지 않는 ID 조회 시 ContentException 발생")
  void getContent_Fail_NotFound() {
    UUID contentId = UUID.randomUUID();

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.getContent(contentId))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND);
  }

  //=========================================================================================
  //콘텐츠 수정 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 수정 성공 - 존재하는 ID로 요청 시 정상 수정 및 DTO 직접 생성 반환")
  void updateContent_Success() {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    ContentUpdateRequest request = new ContentUpdateRequest("수정된 제목",
        "수정된 설명", List.of("스릴러"));
    MockMultipartFile thumbnail = new MockMultipartFile("thumbnail",
        "update.png", "image/png", "dummy".getBytes());

    Content content = new Content(ContentType.MOVIE, "기존 제목", "기존 설명",
        "/uploads/old.png", new ArrayList<>(List.of("액션")));
    ReflectionTestUtils.setField(content, "id", contentId);

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.of(content));

    ContentResponse response = contentService.updateContent(contentId, request, thumbnail);

    assertThat(response).isNotNull();
    assertThat(response.title()).isEqualTo("수정된 제목");
  }

  @Test
  @DisplayName("콘텐츠 수정 성공 - 썸네일 파일이 null이거나 비어있으면 기존 URL을 유지한다")
  void updateContent_Success_KeepThumbnail() {
    UUID contentId = UUID.randomUUID();
    ContentUpdateRequest request = new ContentUpdateRequest("수정된 제목", "수정된 설명", List.of("스릴러"));

    Content content = new Content(ContentType.MOVIE, "기존 제목", "기존 설명",
        "/uploads/old.png", new ArrayList<>(List.of("액션")));
    ReflectionTestUtils.setField(content, "id", contentId);

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.of(content));

    //thumbnail 파라미터에 null을 전달
    ContentResponse response = contentService.updateContent(contentId, request, null);

    assertThat(response).isNotNull();
    assertThat(response.thumbnailUrl()).isEqualTo("/uploads/old.png"); //기존 URL이 그대로 유지되는지 검증
  }

  @Test
  @DisplayName("콘텐츠 수정 실패 - 존재하지 않는 ID 수정 시 ContentException 발생")
  void updateContent_Fail_NotFound() {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    ContentUpdateRequest request = new ContentUpdateRequest("수정된 제목", "수정된 설명", List.of("스릴러"));

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.updateContent(contentId, request, null))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND);
  }

  //=========================================================================================
  //콘텐츠 삭제 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 삭제 성공 - 존재하는 ID로 요청 시 정상 삭제 수행")
  void deleteContent_Success() {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    Content content = new Content(ContentType.MOVIE, "삭제할 영화", "설명", null, List.of());

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.of(content));

    contentService.deleteContent(contentId);

    verify(contentRepository).delete(content);
  }

  @Test
  @DisplayName("콘텐츠 삭제 실패 - 존재하지 않는 ID 삭제 시 ContentException 발생")
  void deleteContent_Fail_NotFound() {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.deleteContent(contentId))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND);
  }
}