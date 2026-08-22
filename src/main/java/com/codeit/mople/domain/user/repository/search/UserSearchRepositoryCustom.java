package com.codeit.mople.domain.user.repository.search;

import java.util.List;
import java.util.UUID;

public interface UserSearchRepositoryCustom {

  List<UUID> findAllByEmailContainingIgnoreCase(String email);
}
