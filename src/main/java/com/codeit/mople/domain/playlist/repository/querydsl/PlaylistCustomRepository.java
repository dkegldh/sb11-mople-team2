package com.codeit.mople.domain.playlist.repository.querydsl;

import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition;
import com.codeit.mople.domain.playlist.entity.Playlist;
import java.util.List;

public interface PlaylistCustomRepository {

  List<Playlist> findAll(PlaylistQueryCondition condition);

  long count(PlaylistQueryCondition condition);

}
