package com.codeit.mople.domain.content.repository.search;

import java.util.List;
import java.util.UUID;

public interface ContentSearchRepositoryCustom {
  List<UUID> findAllByTitleContainingIgnoreCase(String title);
}
