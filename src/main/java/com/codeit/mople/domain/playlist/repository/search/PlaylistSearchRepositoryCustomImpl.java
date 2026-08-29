package com.codeit.mople.domain.playlist.repository.search;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.PlaylistSortBy;
import com.codeit.mople.global.dto.SearchResult;
import com.codeit.mople.global.dto.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PlaylistSearchRepositoryCustomImpl
    implements PlaylistSearchRepositoryCustom {

  private final ElasticsearchOperations elasticsearchOperations;

  @Override
  public SearchResult findAllByTitleContainingIgnoreCase(
      String title,
      UUID cursorId,
      Object cursorValue,
      int limit,
      PlaylistSortBy sortBy,
      SortDirection sortDirection
  ) {

    NativeQuery query = NativeQuery.builder()
        .withQuery(createTitleQuery(title))
        .withPageable(PageRequest.of(0, limit + 1))
        .withSort(getSort(sortBy, sortDirection))
        .build();

    if (cursorId != null && cursorValue != null) {
      query.setSearchAfter(
          List.of(cursorValue instanceof Instant i ? i.toString() : cursorValue,
              cursorId.toString())
      );
    }

    SearchHits<PlaylistDocument> hits =
        elasticsearchOperations.search(
            query,
            PlaylistDocument.class
        );

    List<SearchHit<PlaylistDocument>> searchHits =
        hits.getSearchHits();

    boolean hasNext = searchHits.size() > limit;

    List<SearchHit<PlaylistDocument>> pageHits =
        hasNext
            ? searchHits.subList(0, limit)
            : searchHits;

    if (pageHits.isEmpty()) {
      return new SearchResult(
          List.of(),
          null,
          null,
          false,
          0
      );
    }

    List<UUID> ids = pageHits.stream()
        .map(SearchHit::getContent)
        .map(PlaylistDocument::getId)
        .toList();

    PlaylistDocument last =
        pageHits.get(pageHits.size() - 1).getContent();

    return new SearchResult(
        ids,
        extractCursor(last, sortBy),
        last.getId(),
        hasNext,
        count(title)
    );
  }

  // Sort.Order로 "id"(@Id 필드)를 정렬하면 Spring Data Elasticsearch가
  // 존재하지 않는 "id.keyword" 서브필드로 변환해버려 검색이 깨진다.
  // SortOptions로 실제 매핑된 필드명("id")을 직접 지정해 이를 우회한다.
  private List<SortOptions> getSort(
      PlaylistSortBy sortBy,
      SortDirection sortDirection
  ) {
    SortOrder direction =
        sortDirection == SortDirection.ASCENDING
            ? SortOrder.Asc
            : SortOrder.Desc;

    SortOptions idSort = SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Asc)));

    return switch (sortBy) {
      case UPDATED_AT -> List.of(
          SortOptions.of(s -> s.field(f -> f.field("updatedAt").order(direction))),
          idSort
      );

      case SUBSCRIBE_COUNT -> List.of(
          SortOptions.of(s -> s.field(f -> f.field("subscribeCount").order(direction))),
          idSort
      );
    };
  }

  private String extractCursor(
      PlaylistDocument playlist,
      PlaylistSortBy sortBy
  ) {
    return switch (sortBy) {
      case UPDATED_AT -> playlist.getUpdatedAt().toString();

      case SUBSCRIBE_COUNT -> String.valueOf(playlist.getSubscribeCount());
    };
  }

  private long count(String title) {
    NativeQuery query = NativeQuery.builder()
        .withQuery(createTitleQuery(title))
        .withPageable(PageRequest.of(0, 1))
        .build();

    SearchHits<PlaylistDocument> hits =
        elasticsearchOperations.search(
            query,
            PlaylistDocument.class
        );

    return hits.getTotalHits();
  }

  // n-gram 범위를 벗어날 경우 fallback 처리
  private Query createTitleQuery(String title) {
    if (title.length() < 2 || title.length() > 10) {
      return Query.of(q -> q
          .wildcard(w -> w
              .field("title.keyword")
              .value("*" + title + "*")
              .caseInsensitive(true)
          )
      );
    }

    return Query.of(q -> q
        .match(m -> m
            .field("title")
            .query(title)
        )
    );
  }
}