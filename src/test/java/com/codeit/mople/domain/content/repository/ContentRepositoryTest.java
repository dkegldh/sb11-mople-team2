package com.codeit.mople.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

  private Content content;

  @BeforeEach
  void setUp() {
    content = new Content(
        ContentType.MOVIE,
        "타이타닉",
        "설명1",
        "타이타닉.png",
        List.of("로맨스")
    );
  }

  @Nested
  @DisplayName("콘텐츠 저장 및 조회")
  class SaveAndFindContent {

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

  @Nested
  @DisplayName("외부 ID 리스트로 콘텐츠 일괄 조회")
  class FindByExternalIdIn {

    @Test
    @DisplayName("외부 ID 리스트로 콘텐츠 일괄 조회 테스트")
    void findByExternalIdIn_Success() {
      Content content1 = new Content(
          ContentType.MOVIE, "영화1", "설명1",
          "http://example.com/1.png", new ArrayList<>(), "ext-001"
      );
      Content content2 = new Content(
          ContentType.MOVIE, "영화2", "설명2",
          "http://example.com/2.png", new ArrayList<>(), "ext-002"
      );

      contentRepository.saveAll(List.of(content1, content2));
      entityManager.flush();
      entityManager.clear();

      //존재하는 externalId("ext-001")와 존재하지 않는 ID("ext-999")로 조회
      List<String> searchExternalIds = List.of("ext-001", "ext-999");
      List<Content> foundContents = contentRepository.findByTypeAndExternalIdIn(ContentType.MOVIE, searchExternalIds);
      //일치하는 1개의 콘텐츠만 정상적으로 조회되는지 검증
      assertThat(foundContents).isNotNull();
      assertThat(foundContents).hasSize(1);
      assertThat(foundContents.get(0).getExternalId()).isEqualTo("ext-001");
    }

  }

  @Nested
  @DisplayName("별점 합계, 리뷰 개수 증가")
  class increaseRating {

    @Test
    @DisplayName("별점 합계, 리뷰 개수 증가 성공")
    void increaseRating_success() {
      // given

      // BeforeEach에서 content를 초기화

      contentRepository.save(content);

      entityManager.flush();

      // when
      contentRepository.increaseRating(content.getId(), 4.0);
      contentRepository.increaseRating(content.getId(), 5.0);
      contentRepository.increaseRating(content.getId(), 5.0);

      entityManager.flush();
      entityManager.clear();

      // then
      Content updateContent = contentRepository.findById(content.getId()).orElseThrow();

      assertThat(updateContent.getRatingSum()).isEqualTo(14.0);
      assertThat(updateContent.getReviewCount()).isEqualTo(3);
    }

  }

  @Nested
  @DisplayName("별점 합계 갱신")
  class updateRating {

    @Test
    @DisplayName("별점 합계 수정 성공")
    void updateRating_success() {
      // given

      // BeforeEach에서 content를 초기화

      contentRepository.save(content);

      entityManager.flush();

      // 총 별점 10점으로 지정
      contentRepository.increaseRating(content.getId(), 4.0);
      contentRepository.increaseRating(content.getId(), 4.0);
      contentRepository.increaseRating(content.getId(), 2.0);

      entityManager.flush();

      // when
      contentRepository.updateRating(content.getId(), 4.0, 2.0);

      entityManager.flush();
      entityManager.clear();

      // then
      Content updateContent = contentRepository.findById(content.getId()).orElseThrow();

      assertThat(updateContent.getRatingSum()).isEqualTo(8.0);
      assertThat(updateContent.getReviewCount()).isEqualTo(3);
    }

  }

  @Nested
  @DisplayName("별점 합계, 리뷰 개수 감소")
  class decreaseRating {

    @Test
    @DisplayName("별점 합계, 리뷰 개수 감소 성공")
    void decreaseRating_success() {
      // given

      // BeforeEach에서 content를 초기화

      contentRepository.save(content);

      entityManager.flush();

      // 총 별점 14점, 리뷰 개수 3개로 가정
      contentRepository.increaseRating(content.getId(), 4.0);
      contentRepository.increaseRating(content.getId(), 5.0);
      contentRepository.increaseRating(content.getId(), 5.0);

      entityManager.flush();

      // when
      contentRepository.decreaseRating(content.getId(), 5.0);
      contentRepository.decreaseRating(content.getId(), 4.0);

      entityManager.flush();
      entityManager.clear();

      // then
      Content updateContent = contentRepository.findById(content.getId()).orElseThrow();

      assertThat(updateContent.getRatingSum()).isEqualTo(5.0);
      assertThat(updateContent.getReviewCount()).isEqualTo(1);
    }

  }

}
