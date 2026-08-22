package com.codeit.mople.domain.playlist.repository.search;

import java.util.List;
import java.util.UUID;

public interface PlaylistSearchRepositoryCustom {

  List<UUID> findAllByTitleContainingIgnoreCase(String title);
}