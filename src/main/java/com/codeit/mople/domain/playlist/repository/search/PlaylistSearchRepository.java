package com.codeit.mople.domain.playlist.repository.search;

import java.util.UUID;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PlaylistSearchRepository extends
    ElasticsearchRepository<PlaylistDocument, UUID>,
    PlaylistSearchRepositoryCustom {

}
