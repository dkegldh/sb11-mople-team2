package com.codeit.mople.domain.content.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.content.repository.search.ContentDocument;
import com.codeit.mople.domain.content.repository.search.ContentSearchRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 단순 단위 테스트로 시도하고 싶었으나 H2에서는 Elasticsearch를 지원하지 않음
@SpringBootTest
@ActiveProfiles("test")
class ContentSearchRepositoryIntegrationTest {

  @Autowired
  private ContentSearchRepository contentSearchRepository;

  @BeforeEach
  void setUp() {
    contentSearchRepository.deleteAll();
  }

  @Test
  @DisplayName("콘텐츠 제목으로 검색 성공")
  void findByTitleContainingIgnoreCase_success() {
    // given
    UUID contentId1 = UUID.randomUUID();
    UUID contentId2 = UUID.randomUUID();
    UUID contentId3 = UUID.randomUUID();

    contentSearchRepository.saveAll(List.of(
        new ContentDocument(contentId1, "새 콘텐츠 (1)"),
        new ContentDocument(contentId2, "새 콘텐츠 (2)"),
        new ContentDocument(contentId3, "새 콘텐츠 (3)")
    ));

    // when
    List<UUID> result =
        contentSearchRepository.findAllByTitleContainingIgnoreCase("콘텐츠");

    // then
    assertThat(result)
        .hasSize(3)
        .containsExactlyInAnyOrder(contentId1, contentId2, contentId3);
  }

  @Test
  @DisplayName("콘텐츠 제목을 대소문자 구분 없이 검색 성공")
  void findByTitleContainingIgnoreCase_ignoreCase() {
    // given
    UUID contentId = UUID.randomUUID();

    contentSearchRepository.save(
        new ContentDocument(contentId, "New Content (1)")
    );

    // when
    List<UUID> result = contentSearchRepository.findAllByTitleContainingIgnoreCase("nEw");

    // then
    assertThat(result).hasSize(1)
        .containsExactly(contentId);
  }

  @Test
  @DisplayName("검색 결과가 없으면 빈 목록 반환")
  void findByTitleContainingIgnoreCase_empty() {
    // given
    contentSearchRepository.save(
        new ContentDocument(
            UUID.randomUUID(),
            "새 콘텐츠 (1)"
        )
    );

    // when
    List<UUID> result = contentSearchRepository.findAllByTitleContainingIgnoreCase("33");

    // then
    assertThat(result).isEmpty();
  }

}