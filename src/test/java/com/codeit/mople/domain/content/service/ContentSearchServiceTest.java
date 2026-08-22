package com.codeit.mople.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.content.repository.search.ContentDocument;
import com.codeit.mople.domain.content.repository.search.ContentSearchRepository;
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
class ContentSearchServiceTest {

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentSearchRepository contentSearchRepository;

  @InjectMocks
  private ContentSearchService contentSearchService;

  private UUID contentId1;
  private UUID contentId2;
  private Content content1;
  private Content content2;

  @BeforeEach
  void setUp() {
    contentId1 = UUID.randomUUID();
    contentId2 = UUID.randomUUID();

    content1 = mock(Content.class);
    content2 = mock(Content.class);
  }

  @Test
  @DisplayName("검색 문서로 저장 성공")
  void indexAll_success() {
    // given

    // BeforeEach에서 content1, content2를 초기화

    when(content1.getId()).thenReturn(contentId1);
    when(content1.getTitle()).thenReturn("콘텐츠1");

    when(content2.getId()).thenReturn(contentId2);
    when(content2.getTitle()).thenReturn("콘텐츠2");

    when(contentRepository.findAll())
        .thenReturn(List.of(content1, content2));

    // when
    contentSearchService.indexAll();

    // then
    ArgumentCaptor<List<ContentDocument>> captor = ArgumentCaptor.forClass(List.class);

    verify(contentSearchRepository).saveAll(captor.capture());

    List<ContentDocument> documents = captor.getValue();

    assertThat(documents).hasSize(2);

    assertThat(documents)
        .extracting(ContentDocument::getId)
        .containsExactly(contentId1, contentId2);
    assertThat(documents)
        .extracting(ContentDocument::getTitle)
        .containsExactly("콘텐츠1", "콘텐츠2");
  }

  @Test
  @DisplayName("검색 문서로 저장 성공 - 콘텐츠가 없을 경우 빈 검색 문서를 저장")
  void indexAll_success_empty() {
    // given
    when(contentRepository.findAll())
        .thenReturn(List.of());

    // when
    contentSearchService.indexAll();

    // then
    ArgumentCaptor<List<ContentDocument>> captor = ArgumentCaptor.forClass(List.class);

    verify(contentSearchRepository).saveAll(captor.capture());

    assertThat(captor.getValue()).isEmpty();
  }

}