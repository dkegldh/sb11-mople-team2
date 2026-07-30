package com.codeit.mople.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.content.dto.ContentCreateRequest;
import com.codeit.mople.domain.content.dto.ContentPageResponse;
import com.codeit.mople.domain.content.dto.ContentResponse;
import com.codeit.mople.domain.content.dto.ContentUpdateRequest;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.mapper.ContentMapper;
import com.codeit.mople.domain.content.repository.ContentRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
public class ContentServiceTest {

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentMapper contentMapper;

  @InjectMocks
  private ContentService contentService;

  //=========================================================================================
  //콘텐츠 생성 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 생성 성공 - 레포지토리 저장 및 DTO 변환 정상적 수행")
  void createContent_Success() {
    UUID adminId = UUID.randomUUID();
    ContentCreateRequest request = new ContentCreateRequest("MOVIE", "테스트 영화",
        "설명", List.of("액션"));
    MockMultipartFile thumbnail = new MockMultipartFile("thumbnail",
        "test.png", "image/png", "dummy".getBytes());

    Content savedContent = new Content(ContentType.MOVIE, "테스트 영화", "설명",
        "/uploads/test.png", List.of("액션"));
    ContentResponse expectedResponse = new ContentResponse(UUID.randomUUID(), "MOVIE",
        "테스트 영화", "설명", "/uploads/test.png",
        List.of("액션"), 0.0, 0, 0L);

    given(contentMapper.toEntity(any(), any(), any())).willReturn(savedContent);
    given(contentRepository.save(any(Content.class))).willReturn(savedContent);
    given(contentMapper.toDto(savedContent)).willReturn(expectedResponse);

    ContentResponse response = contentService.createContent(adminId, request, thumbnail);

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

    assertThatThrownBy(() -> contentService.createContent(adminId, request, null))
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

    assertThatThrownBy(() -> contentService.createContent(adminId, request, invalidFile))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_IMAGE_FILE);
  }

  //=========================================================================================
  //콘텐츠 목록 조회 테스트
  //=========================================================================================

  @Test
  @DisplayName("콘텐츠 목록 조회 성공 - 요청한 조건에 맞게 페이징된 데이터 반환")
  void getContents_Success() {
    int limit = 10;
    String sortDirection = "DESCENDING";
    String sortBy = "createdAt";

    Content content1 = new Content(ContentType.MOVIE, "영화1",
        "설명", null, List.of());
    Page<Content> contentPage = new PageImpl<>(List.of(content1));

    ContentResponse responseDto = new ContentResponse(UUID.randomUUID(),
        "MOVIE", "영화1", "설명", null, List.of(),
        0.0, 0, 0L);

    ContentPageResponse expectedPageResponse = new ContentPageResponse(
        List.of(responseDto), null, null, false, 1L, sortBy, sortDirection);

    given(contentRepository.findAll(any(PageRequest.class))).willReturn(contentPage);
    given(contentMapper.toDto(content1)).willReturn(responseDto);
    given(contentMapper.toPageResponse(any(), any(), any(), any())).willReturn(expectedPageResponse);

    ContentPageResponse response = contentService.getContents(0, limit, sortDirection, sortBy);

    assertThat(response).isNotNull();
    assertThat(response.data()).hasSize(1);
    assertThat(response.data().get(0).title()).isEqualTo("영화1");
    assertThat(response.totalCount()).isEqualTo(1);
    assertThat(response.sortDirection()).isEqualTo("DESCENDING");
  }

  @Test
  @DisplayName("콘텐츠 목록 조회 실패 - limit 값이 0 이하일 경우 ContentException 발생")
  void getContents_Fail_NegativeLimit() {
    assertThatThrownBy(() -> contentService.getContents(0, -1, "ASCENDING", "createdAt"))
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
    Content content = new Content(ContentType.MOVIE, "단건 조회 영화",
        "설명", null, List.of());
    ContentResponse responseDto = new ContentResponse(contentId, "MOVIE",
        "단건 조회 영화", "설명", null, List.of(),
        0.0, 0, 0L);

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.of(content));
    given(contentMapper.toDto(content)).willReturn(responseDto);

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
  @DisplayName("콘텐츠 수정 성공 - 존재하는 ID로 요청 시 정상 수정 및 DTO 반환")
  void updateContent_Success() {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    ContentUpdateRequest request = new ContentUpdateRequest("수정된 제목",
        "수정된 설명", List.of("스릴러"));
    MockMultipartFile thumbnail = new MockMultipartFile("thumbnail",
        "update.png", "image/png", "dummy".getBytes());

    Content content = new Content(ContentType.MOVIE, "기존 제목", "기존 설명",
        "/uploads/old.png", new ArrayList<>(List.of("액션")));

    ContentResponse expectedResponse = new ContentResponse(contentId, "MOVIE",
        "수정된 제목", "수정된 설명", "/uploads/update.png",
        List.of("스릴러"), 0.0, 0, 0L);

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.of(content));
    given(contentMapper.toDto(content)).willReturn(expectedResponse);

    ContentResponse response = contentService.updateContent(adminId, contentId, request, thumbnail);

    assertThat(response).isNotNull();
    assertThat(response.title()).isEqualTo("수정된 제목");
  }

  @Test
  @DisplayName("콘텐츠 수정 실패 - 존재하지 않는 ID 수정 시 ContentException 발생")
  void updateContent_Fail_NotFound() {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    ContentUpdateRequest request = new ContentUpdateRequest("수정된 제목", "수정된 설명", List.of("스릴러"));

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.updateContent(adminId, contentId, request, null))
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

    contentService.deleteContent(adminId, contentId);

    verify(contentRepository).delete(content);
  }

  @Test
  @DisplayName("콘텐츠 삭제 실패 - 존재하지 않는 ID 삭제 시 ContentException 발생")
  void deleteContent_Fail_NotFound() {
    UUID contentId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    given(contentRepository.findById(any(UUID.class))).willReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.deleteContent(adminId, contentId))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND);
  }
}