package com.codeit.mople.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
public class ContentRepositoryTest {

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  @DisplayName("콘텐츠 저장 및 조회 테스트 - 엔티티 매핑 및 컬렉션(tags) 정상 작동 확인")
  void saveAndFindContent_Success() {
    Content content = new Content(ContentType.MOVIE, "테스트 영화", "설명",
        "http://example.com/thumb.png", new ArrayList<>(List.of("액션", "스릴러")));

    Content savedContent = contentRepository.save(content);

    entityManager.flush();
    entityManager.clear();

    Content foundContent = contentRepository.findById(savedContent.getId()).orElseThrow();

    assertThat(foundContent).isNotNull();
    assertThat(foundContent.getTitle()).isEqualTo("테스트 영화");
    assertThat(foundContent.getType()).isEqualTo(ContentType.MOVIE);

    //별도 테이블로 빠지는 @ElementCollection 데이터가 잘 저장되고 불러와지는지 검증
    assertThat(foundContent.getTags()).containsExactly("액션", "스릴러");

    //BaseTimeEntity를 통한 Auditing 필드 검증
    assertThat(foundContent.getCreatedAt()).isNotNull();
    assertThat(foundContent.getUpdatedAt()).isNotNull();
  }
}
