package com.codeit.mople.domain.user.repository.search;

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
public class UserSearchRepositoryCustomImpl
    implements UserSearchRepositoryCustom {

  private static final int BATCH_SIZE = 1000;

  private final ElasticsearchOperations elasticsearchOperations;

  @Override
  public List<UUID> findAllByEmailContainingIgnoreCase(String email) {

    List<UUID> userIds = new ArrayList<>();
    List<Object> searchAfter = null;

    while (true) {
      NativeQuery query = NativeQuery.builder()
          .withQuery(q -> q
              .wildcard(w -> w
                  .field("email")
                  .value("*" + escapeWildcard(email) + "*")
                  .caseInsensitive(true)
              )
          )
          .withPageable(PageRequest.of(0, BATCH_SIZE))
          .withSort(
              Sort.by(Sort.Direction.ASC, "id.keyword")
          )
          .build();

      if (searchAfter != null && !searchAfter.isEmpty()) {
        query.setSearchAfter(searchAfter);
      }

      SearchHits<UserDocument> hits =
          elasticsearchOperations.search(
              query,
              UserDocument.class
          );

      List<SearchHit<UserDocument>> searchHits =
          hits.getSearchHits();

      if (searchHits.isEmpty()) {
        break;
      }

      userIds.addAll(
          searchHits.stream()
              .map(SearchHit::getContent)
              .map(UserDocument::getId)
              .toList()
      );

      if (searchHits.size() < BATCH_SIZE) {
        break;
      }

      searchAfter =
          searchHits.get(searchHits.size() - 1)
              .getSortValues();
    }

    return userIds;
  }

  private String escapeWildcard(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("*", "\\*")
        .replace("?", "\\?");
  }
}