package com.codeit.mople.domain.playlist.repository.querydsl;

import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition;
import com.codeit.mople.domain.playlist.entity.Playlist;
import java.util.List;
import java.util.UUID;

public interface PlaylistCustomRepository {

  List<Playlist> findAll(PlaylistQueryCondition condition, List<UUID> playlistIds);

  long count(PlaylistQueryCondition condition, List<UUID> playlistIds);

}
