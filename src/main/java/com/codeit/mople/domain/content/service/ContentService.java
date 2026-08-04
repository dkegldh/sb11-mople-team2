package com.codeit.mople.domain.content.service;

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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService{

  private final ContentRepository contentRepository;
  private final ContentQueryRepository contentQueryRepository;

  //허용할 이미지 MIME 타입 및 확장자 정의
  private static final List<String> ALLOWED_MIME_TYPES = List.of(
      "image/jpeg", "image/png", "image/webp", "image/gif"
  );
  private static final List<String> ALLOWED_EXTENSIONS = List.of(
      "jpg", "jpeg", "png", "webp", "gif"
  );

  //콘텐츠 생성
  @Transactional
  public ContentResponse createContent(ContentCreateRequest request, MultipartFile thumbnail) {
    log.debug("콘텐트 생성 시작 - type: {}, title: {}", request.type(), request.title());

    //ContentType 변환 방어 로직
    ContentType contentType;
    try {
      contentType = ContentType.valueOf(request.type().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("콘텐츠 생성 실패(잘못된 ContentType) - type: {}", request.type());
      throw new ContentException(ContentErrorCode.INVALID_CONTENT_TYPE, Map.of("type", request.type()));
    }

    //썸네일 이미지 업로드 처리(현재는 임시 URL 처리)
    String uploadedThumbnailUrl = null;
    if (thumbnail != null && !thumbnail.isEmpty()) {
      uploadedThumbnailUrl = saveThumbnail(thumbnail);
    }

    //Request DTO 데이터를 바탕으로 매퍼를 통해 Content 엔티티 생성
    Content content = new Content(contentType, request.title(), request.description(), uploadedThumbnailUrl, request.tags());

    //DB에 엔티티 저장
    Content savedContent = contentRepository.save(content);

    log.info("콘텐츠 생성 완료 - contentId: {}, title: {}", savedContent.getId(), savedContent.getTitle());

    return new ContentResponse(
        savedContent.getId(),
        savedContent.getType().name(),
        savedContent.getTitle(),
        savedContent.getDescription(),
        savedContent.getThumbnailUrl(),
        savedContent.getTags(),
        savedContent.getAverageRating(),
        savedContent.getReviewCount(),
        savedContent.getWatcherCount()
    );
  }

  //콘텐츠 목록 조회
  @Transactional(readOnly = true)
  public CursorResponseContentDto getContents(UUID cursorId, Instant cursorCreatedAt, int limit) {
    log.debug("콘텐츠 목록 조회 시작 - cursorId: {}, cursorCreatedAt: {}, limit: {}", cursorId, cursorCreatedAt, limit);

    if (limit <= 0 || limit > 100) {
      log.warn("콘텐츠 목록 조회 실패(잘못된 페이징 조건) - limit: {}", limit);
      throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST, Map.of("limit", limit));
    }

    //커서 피라미터가 둘 중 하나만 드어온 경우 예외 처리
    if ((cursorId == null) != (cursorCreatedAt == null)) {
      log.warn("콘텐츠 목록 조회 실패(불완전한 커서 조건) - cursorId: {}, cursorCreatedAt: {}", cursorId, cursorCreatedAt);
      throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST,
          Map.of("cursorId", String.valueOf(cursorId), "cursorCreatedAt", String.valueOf(cursorCreatedAt)));
    }

    //데이터 조회 및 카운트
    List<Content> contents = contentQueryRepository.findContentByCursor(cursorId, cursorCreatedAt, limit);
    long totalCount = contentQueryRepository.countAllContents();

    //hasNext 판단 및 리스트 자르기
    boolean hasNext = contents.size() > limit;
    List<Content> pageContents = hasNext ? contents.subList(0, limit) : contents;

    List<ContentResponse> contentResponses = pageContents.stream()
        .map(content -> new ContentResponse(
            content.getId(),
            content.getType().name(),
            content.getTitle(),
            content.getDescription(),
            content.getThumbnailUrl(),
            content.getTags(),
            content.getAverageRating(),
            content.getReviewCount(),
            content.getWatcherCount()
        )).toList();

    //커서 값 추출
    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext && !pageContents.isEmpty()) {
      Content lastItem = pageContents.get(pageContents.size() - 1);
      nextCursor = lastItem.getCreatedAt() != null ? lastItem.getCreatedAt().toString() : null;
      nextIdAfter = lastItem.getId();
    }

    log.debug("콘텐츠 목록 조회 완료 - 조회된 데이터 개수: {}", contentResponses.size());

    //응답 조립 후 반환
    return new CursorResponseContentDto(
        contentResponses,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "createdAt",
        "DESCENDING"
    );
  }

  //콘텐츠 단건 조회
  @Transactional(readOnly = true)
  public ContentResponse getContent(UUID contentId) {
    log.debug("콘텐츠 단건 조회 시작 - contentId: {}", contentId);

    //ID로 DB에서 콘텐츠 조회
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> {
          log.warn("콘텐츠 단건 조회 실패(존재하지 않는 ID) - contentId: {}", contentId);
          return new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("contentId", contentId));
        });

    log.debug("콘텐츠 단건 조회 완료 - contentId: {}", content.getId());

    return new ContentResponse(
        content.getId(),
        content.getType().name(),
        content.getTitle(),
        content.getDescription(),
        content.getThumbnailUrl(),
        content.getTags(),
        content.getAverageRating(),
        content.getReviewCount(),
        content.getWatcherCount()
    );
  }

  //콘텐츠 수정
  @Transactional
  public ContentResponse updateContent(UUID contentId, ContentUpdateRequest request, MultipartFile thumbnail) {
    log.debug("콘텐츠 수정 시작 - contentId: {}, updateTitle: {}", contentId, request.title());

    //수정할 콘텐츠 조회(없으면 404 예외 발생)
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> {
          log.warn("콘텐츠 수정 실패(존재하지 않는 ID) - contentId: {}", contentId);
          return new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("contentId", contentId));        });

    //썸네일 수정(새로운 파일이 들어온 경우에만 업데이트)
    String uploadedThumbnailUrl = content.getThumbnailUrl(); //기존 URL 유지
    if (thumbnail != null && !thumbnail.isEmpty()) {
      uploadedThumbnailUrl = saveThumbnail(thumbnail);
    }

    //엔티티 상태 변경(JPA 변경 감지 활용)
    content.updateContentInfo(
        request.title(),
        request.description(),
        uploadedThumbnailUrl,
        request.tags()
    );

    log.info("콘텐츠 수정 완료 - contentId: {}", content.getId());

    return new ContentResponse(
        content.getId(),
        content.getType().name(),
        content.getTitle(),
        content.getDescription(),
        content.getThumbnailUrl(),
        content.getTags(),
        content.getAverageRating(),
        content.getReviewCount(),
        content.getWatcherCount()
    );
  }

  //콘텐츠 삭제
  @Transactional
  public void deleteContent(UUID contentId) {
    log.debug("콘텐츠 삭제 시작 - contentId: {}", contentId);

    //삭제할 콘텐츠 조회
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> {
          log.warn("콘텐츠 삭제 실패(존재하지 않는 ID) - contentId: {}", contentId);
          return new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("contentId", contentId));
        });

    //조회된 엔티티 삭제
    contentRepository.delete(content);

    log.info("콘텐츠 삭제 완료 - contentId: {}", contentId);
  }

  //썸네일 검증 및 파일 시스템 저장 메서드
  private String saveThumbnail(MultipartFile thumbnail) {
    String extension = validateAndGetExtension(thumbnail);
    String safeFilename = UUID.randomUUID() + "." + extension;

    // 로컬 저장 경로 설정 (프로젝트 루트의 uploads 폴더)
    String uploadDir = "uploads/";
    File dir = new File(uploadDir);
    if (!dir.exists()) {
      dir.mkdirs(); // 폴더가 없으면 생성
    }

    try {
      // 실제 파일 저장
      Path filePath = Paths.get(uploadDir, safeFilename);
      Files.write(filePath, thumbnail.getBytes());

      //TODO: 추후 AWS S3 등에 업로드하고 반환된 URL 사용 예정
      String uploadedThumbnailUrl = "/uploads/" + safeFilename;
      log.debug("썸네일 임시 저장 완료 - url: {}", uploadedThumbnailUrl);

      return uploadedThumbnailUrl;
    } catch (IOException e) {
      log.error("썸네일 파일 업로드 중 예상치 못한 오류 발생", e);
      throw new ContentException(ContentErrorCode.IMAGE_UPLOAD_FAILED,
          Map.of("filename", thumbnail.getOriginalFilename() != null ? thumbnail.getOriginalFilename() : "null"));
    }
  }

  //썸네일 파일 MIME 타입 및 확장자 유효성 검사 검증 메서드
  private String validateAndGetExtension(MultipartFile file) {
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
      log.warn("이미지 업로드 실패 - 허용되지 않은 Content-Type: {}", contentType);
      throw new ContentException(ContentErrorCode.INVALID_IMAGE_FILE, Map.of("contentType", contentType != null ? contentType : "null"));
    }

    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null || !originalFilename.contains(".")) {
      log.warn("이미지 업로드 실패 - 확장자가 없는 파일명: {}", originalFilename);
      throw new ContentException(ContentErrorCode.INVALID_IMAGE_FILE, Map.of("originalFilename", originalFilename != null ? originalFilename : "null"));
    }

    String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      log.warn("이미지 업로드 실패 - 허용되지 않은 확장자: {}", extension);
      throw new ContentException(ContentErrorCode.INVALID_IMAGE_FILE, Map.of("extension", extension));
    }

    return extension;
  }
}
