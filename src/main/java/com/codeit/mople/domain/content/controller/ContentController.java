package com.codeit.mople.domain.content.controller;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.content.controller.api.ContentApi;
import com.codeit.mople.domain.content.dto.ContentCreateRequest;
import com.codeit.mople.domain.content.dto.ContentResponse;
import com.codeit.mople.domain.content.dto.ContentUpdateRequest;
import com.codeit.mople.domain.content.dto.CursorResponseContentDto;
import com.codeit.mople.domain.content.service.ContentService;
import com.codeit.mople.domain.watchingsession.service.WatchingSessionService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/contents")
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ContentController implements ContentApi {

  private final ContentService contentService;
  private final WatchingSessionService watchingSessionService;

  //콘텐츠 생성
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ContentResponse> createContent(
      @Valid @RequestPart("request") ContentCreateRequest request,
      @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
      @AuthenticationPrincipal(errorOnInvalidType = true) CustomUserDetails userDetails) {

    ContentResponse response = contentService.createContent(request, thumbnail);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  //콘텐츠 목록 조회
  @Override
  @GetMapping
  public ResponseEntity<CursorResponseContentDto> getContents(
      @RequestParam(value = "cursorId", required = false) UUID cursorId,
      @RequestParam(value = "cursorCreatedAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursorCreatedAt,
      @RequestParam(value = "limit", defaultValue = "10") int limit) {

    CursorResponseContentDto response = contentService.getContents(cursorId, cursorCreatedAt, limit);
    return ResponseEntity.ok(response);
  }

  //콘텐츠 단건 조회
  @Override
  @GetMapping("/{contentId}")
  public ResponseEntity<ContentResponse> getContent(
      @PathVariable UUID contentId) {
    ContentResponse response = contentService.getContent(contentId);
    return ResponseEntity.ok(response);
  }

  //콘텐츠 수정
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping(value = "/{contentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ContentResponse> updateContent(
      @PathVariable UUID contentId,
      @Valid @RequestPart("request") ContentUpdateRequest request,
      @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {

    ContentResponse response = contentService.updateContent(contentId, request, thumbnail);
    return ResponseEntity.ok(response);
  }

  //콘텐츠 삭제
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{contentId}")
  public ResponseEntity<Void> deleteContent(
      @PathVariable UUID contentId) {
    contentService.deleteContent(contentId);
    return ResponseEntity.noContent().build();
  }
}