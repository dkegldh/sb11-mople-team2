package com.codeit.mople.domain.playlist.service;

import com.codeit.mople.domain.playlist.repository.PlaylistRepository;
import com.codeit.mople.domain.playlist.repository.search.PlaylistDocument;
import com.codeit.mople.domain.playlist.repository.search.PlaylistSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaylistSearchService {

  private final PlaylistRepository playlistRepository;
  private final PlaylistSearchRepository playlistSearchRepository;

  @Transactional(readOnly = true)
  public void indexAll() {
    // 플레이리스트 제목들을 저장소에 저장함
    var documents = playlistRepository.findAll().stream()
        .map(playlist -> new PlaylistDocument(
            playlist.getId(),
            playlist.getTitle()
        ))
        .toList();

    playlistSearchRepository.saveAll(documents);
  }
}