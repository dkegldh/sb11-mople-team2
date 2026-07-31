package com.codeit.mople.domain.review.repository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.review.entity.Review;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@DataJpaTest
public class ReviewRepositoryTest {

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private ReviewRepository reviewRepository;

  private User author;
  private Content content1;
  private Content content2;
  private Review review1;
  private Review review2;
  private Review review3;

  @BeforeEach
  void setUp() {
    author = User.createUser("test@test.com", "12345678", "test");
    content1 = new Content(
        ContentType.DRAMA,
        "test",
        "test 콘텐츠",
        "test/image.png",
        List.of("공포")
    );
    content2 = new Content(
        ContentType.MOVIE,
        "test",
        "test 콘텐츠",
        "test/image.png",
        List.of("코미디")
    );

    entityManager.persist(author);
    entityManager.persist(content1);
    entityManager.persist(content2);

    review1 = Review.create(content1, author, "리뷰 내용1", 5.0);
    review2 = Review.create(content1, author, "리뷰 내용2", 3.0);
    review3 = Review.create(content2, author, "리뷰 내용3", 4.0);
  }

  @Nested
  @DisplayName("countByContentId 테스트")
  class CountByContentId {
    
    @Test
    @DisplayName("특정 콘텐츠의 리뷰 개수 조회 성공")
    void count_success() {
      // given

      // BeforeEach에서 author, content1, content2, review1, review2, review3을 초기화

      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);

      entityManager.flush();
      entityManager.clear();

      // when
      long count1 = reviewRepository.countByContentId(content1.getId());
      long count2 = reviewRepository.countByContentId(content2.getId());

      // then
      assertThat(count1).isEqualTo(2);
      assertThat(count2).isEqualTo(1);
    }
    
    @Test
    @DisplayName("특정 콘텐츠에 리뷰가 없을 경우 0을 반환")
    void count_zero() {
      // given

      // BeforeEach에서 content1을 초기화

      // when
      long count = reviewRepository.countByContentId(content1.getId());
      
      // then
      assertThat(count).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("findAverageRatingByContentId 테스트")
  class FindAverageRatingByContentId {

    @Test
    @DisplayName("특정 콘텐츠의 평균 평점을 조회")
    void average_success() {
      // given

      // BeforeEach에서 content1, review1, review2를 초기화
      entityManager.persist(review1);
      entityManager.persist(review2);

      entityManager.flush();
      entityManager.clear();

      // when
      Double average = reviewRepository.findAverageRatingByContentId(content1.getId());

      // then
      assertThat(average).isEqualTo(4.0); // (5.0 + 3.0) / 2 = 4.0
    }

    @Test
    @DisplayName("특정 콘텐츠에 리뷰가 없을 경우 null을 반환")
    void average_null() {
      // given

      // BeforeEach에서 content1을 초기화

      // when
      Double average = reviewRepository.findAverageRatingByContentId(content1.getId());

      // then
      assertThat(average).isNull();
    }
  }

}
