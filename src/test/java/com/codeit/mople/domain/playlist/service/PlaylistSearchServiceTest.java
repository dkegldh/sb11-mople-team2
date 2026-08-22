package com.codeit.mople.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.repository.PlaylistRepository;
import com.codeit.mople.domain.playlist.repository.search.PlaylistDocument;
import com.codeit.mople.domain.playlist.repository.search.PlaylistSearchRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaylistSearchServiceTest {

  @Mock
  private PlaylistRepository playlistRepository;

  @Mock
  private PlaylistSearchRepository playlistSearchRepository;

  @InjectMocks
  private PlaylistSearchService playlistSearchService;

  private UUID playlistId1;
  private UUID playlistId2;
  private Playlist playlist1;
  private Playlist playlist2;

  @BeforeEach
  void setUp() {
    playlistId1 = UUID.randomUUID();
    playlistId2 = UUID.randomUUID();

    playlist1 = mock(Playlist.class);
    playlist2 = mock(Playlist.class);
  }

  @Test
  @DisplayName("검색 문서로 저장 성공")
  void indexAll_success() {
    // given

    // BeforeEach에서 playlist1, playlist2를 초기화

    when(playlist1.getId()).thenReturn(playlistId1);
    when(playlist1.getTitle()).thenReturn("플레이리스트1");

    when(playlist2.getId()).thenReturn(playlistId2);
    when(playlist2.getTitle()).thenReturn("플레이리스트2");

    when(playlistRepository.findAll())
        .thenReturn(List.of(playlist1, playlist2));

    // when
    playlistSearchService.indexAll();

    // then
    ArgumentCaptor<List<PlaylistDocument>> captor = ArgumentCaptor.forClass(List.class);

    verify(playlistSearchRepository).saveAll(captor.capture());

    List<PlaylistDocument> documents = captor.getValue();

    assertThat(documents).hasSize(2);

    assertThat(documents)
        .extracting(PlaylistDocument::getId)
        .containsExactly(playlistId1, playlistId2);
    assertThat(documents)
        .extracting(PlaylistDocument::getTitle)
        .containsExactly("플레이리스트1", "플레이리스트2");
  }

  @Test
  @DisplayName("검색 문서로 저장 성공 - 플레이리스트가 없을 경우 빈 검색 문서를 저장")
  void indexAll_success_empty() {
    // given
    when(playlistRepository.findAll())
        .thenReturn(List.of());

    // when
    playlistSearchService.indexAll();

    // then
    ArgumentCaptor<List<PlaylistDocument>> captor = ArgumentCaptor.forClass(List.class);

    verify(playlistSearchRepository).saveAll(captor.capture());

    assertThat(captor.getValue()).isEmpty();
  }

}