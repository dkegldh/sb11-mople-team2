package com.codeit.mople.domain.user.repository.search;

import java.util.UUID;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface UserSearchRepository extends
    ElasticsearchRepository<UserDocument, UUID>,
    UserSearchRepositoryCustom {

}
