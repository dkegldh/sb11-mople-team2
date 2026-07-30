package com.codeit.mople.domain.content.mapper;

import com.codeit.mople.domain.content.dto.ContentCreateRequest;
import com.codeit.mople.domain.content.dto.ContentPageResponse;
import com.codeit.mople.domain.content.dto.ContentResponse;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class ContentMapper {

  //Entity -> DTO 변환
  public ContentResponse toDto(Content content) {
    if (content == null) {
      return  null;
    }

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

  //Request -> Entity 변환
  public Content toEntity(ContentCreateRequest request, ContentType contentType,
      String uploadedThumbnailUrl) {
    return new Content(
        contentType,
        request.title(),
        request.description(),
        uploadedThumbnailUrl,
        request.tags()
    );
  }

  //Page 데이터 -> PageResponse 변환
  public ContentPageResponse toPageResponse(List<ContentResponse> contentResponses, Page<Content> contentPage, String sortBy, String sortDirection) {
    return new ContentPageResponse(
        contentResponses,
        null, //nextCursor(당장 미사용)
        null, //nextIdAfter(당장 미사용)
        contentPage.hasNext(), //다음 페이지 존재 여부
        contentPage.getTotalElements(), //전체 데이터 개수
        sortBy,
        sortDirection
    );
  }
}
