package com.codeit.mople.domain.content.service;

import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.content.repository.search.ContentDocument;
import com.codeit.mople.domain.content.repository.search.ContentSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentSearchService {

  private final ContentRepository contentRepository;
  private final ContentSearchRepository contentSearchRepository;

  @Transactional(readOnly = true)
  public void indexAll() {
    // 콘텐츠 제목들을 저장소에 저장함
    var documents = contentRepository.findAll().stream()
        .map(content -> new ContentDocument(
            content.getId(),
            content.getTitle()
        ))
        .toList();

    contentSearchRepository.saveAll(documents);
  }
}