package com.codeit.mople.domain.content.repository.search;

import java.util.UUID;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ContentSearchRepository extends
    ElasticsearchRepository<ContentDocument, UUID>,
    ContentSearchRepositoryCustom {

}
