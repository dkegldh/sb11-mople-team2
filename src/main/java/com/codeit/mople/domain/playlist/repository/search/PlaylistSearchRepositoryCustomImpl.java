package com.codeit.mople.domain.playlist.repository.search;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PlaylistSearchRepositoryCustomImpl
    implements PlaylistSearchRepositoryCustom {

  private static final int BATCH_SIZE = 1000;

  private final ElasticsearchOperations elasticsearchOperations;

  @Override
  public List<UUID> findAllByTitleContainingIgnoreCase(String title) {

    List<UUID> playlistIds = new ArrayList<>();
    List<Object> searchAfter = null;

    while (true) {
      NativeQuery query = NativeQuery.builder()
          .withQuery(q -> q
              .wildcard(w -> w
                  .field("title")
                  .value("*" + escapeWildcard(title) + "*")
                  .caseInsensitive(true)
              )
          )
          .withPageable(PageRequest.of(0, BATCH_SIZE))
          .withSort(Sort.by(Sort.Direction.ASC, "id.keyword"))
          .build();

      if (searchAfter != null && !searchAfter.isEmpty()) {
        query.setSearchAfter(searchAfter);
      }

      SearchHits<PlaylistDocument> hits =
          elasticsearchOperations.search(query, PlaylistDocument.class);

      List<SearchHit<PlaylistDocument>> searchHits =
          hits.getSearchHits();

      if (searchHits.isEmpty()) {
        break;
      }

      playlistIds.addAll(
          searchHits.stream()
              .map(SearchHit::getContent)
              .map(PlaylistDocument::getId)
              .toList()
      );

      if (searchHits.size() < BATCH_SIZE) {
        break;
      }

      searchAfter =
          searchHits.get(searchHits.size() - 1)
              .getSortValues();
    }

    return playlistIds;
  }

  private String escapeWildcard(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("*", "\\*")
        .replace("?", "\\?");
  }
}