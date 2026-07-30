package com.codeit.mople.domain.content.controller;

import com.codeit.mople.domain.content.dto.ContentCreateRequest;
import com.codeit.mople.domain.content.dto.ContentPageResponse;
import com.codeit.mople.domain.content.dto.ContentResponse;
import com.codeit.mople.domain.content.dto.ContentUpdateRequest;
import com.codeit.mople.domain.content.service.ContentService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequiredArgsConstructor
public class ContentController {
  private final ContentService contentService;

  //콘텐츠 생성
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ContentResponse> createContent(
      //TODO: 추후 JWT 도입 및 관리자 권한 적용 시 주석 해제
      //@RequestHeader("X-User-Id")UUID adminId,
      @Valid @RequestPart("request")ContentCreateRequest request,
      @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {

    //TODO: JWT 도입 시 아래 임시 adminId 삭제 예정
    UUID adminId = UUID.randomUUID();

    ContentResponse response = contentService.createContent(adminId, request, thumbnail);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  //콘텐츠 목록 조회
  @GetMapping
  public ResponseEntity<ContentPageResponse> getContents(
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "limit", defaultValue = "10") int limit,
      @RequestParam(value = "sortDirection", defaultValue = "DESCENDING") String sortDirection,
      @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy) {
    ContentPageResponse response = contentService.getContents(page, limit, sortDirection, sortBy);
    return ResponseEntity.ok(response);
  }

  //콘텐츠 단건 조회
  @GetMapping("/{contentId}")
  public ResponseEntity<ContentResponse> getContent(
      @PathVariable UUID contentId) {
    ContentResponse response = contentService.getContent(contentId);
    return ResponseEntity.ok(response);
  }

  //콘텐츠 수정
  @PatchMapping(value = "/{contentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ContentResponse> updateContent(
      @PathVariable UUID contentId,
      //TODO: 추후 JWT 도입 및 관리자 권한 적용 시 주석 해제
      //@RequestHeader("X-User-Id") UUID adminId,
      @Valid @RequestPart("request")ContentUpdateRequest request,
      @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {

    //TODO: JWT 도입 시 아래 임시 adminId 삭제 예정
    UUID adminId = UUID.randomUUID();

    ContentResponse response = contentService.updateContent(adminId, contentId, request, thumbnail);
    return ResponseEntity.ok(response);
  }

  //콘텐츠 삭제
  @DeleteMapping("/{contentId}")
  public ResponseEntity<Void> deleteContent(
      @PathVariable UUID contentId
      //TODO: 추후 JWT 도입 및 관리자 권한 적용 시 주석 해제
      //,@RequestHeader("X-User-Id") UUID adminId
  ) {

    //TODO: JWT 도입 시 아래 임시 adminId 삭제 예정
    UUID adminId = UUID.randomUUID();

    contentService.deleteContent(adminId, contentId);

    return ResponseEntity.ok().build();
  }
}
