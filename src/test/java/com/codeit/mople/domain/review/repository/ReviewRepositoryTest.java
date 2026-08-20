package com.codeit.mople.domain.review.repository;


import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition.ReviewSortBy;
import com.codeit.mople.domain.review.entity.Review;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import com.codeit.mople.global.dto.SortDirection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
  private User user1;
  private User user2;
  private Content content1;
  private Content content2;
  private Review review1;
  private Review review2;
  private Review review3;

  @BeforeEach
  void setUp() {
    author = User.createUser("test@test.com", "12345678", "test");
    user1 = User.createUser("user1@test.com", "12345678", "user1");
    user2 = User.createUser("user2@test.com", "12345678", "user2");
    content1 = new Content(
        ContentType.TV_SERIES,
        "test",
        "test 콘텐츠",
        "test/image.png",
        List.of("공포")
    );
    content2 = new Content(
        ContentType.MOVIE,
        "test2",
        "test 콘텐츠",
        "test/image.png",
        List.of("코미디")
    );

    entityManager.persist(author);
    entityManager.persist(user1);
    entityManager.persist(user2);
    entityManager.persist(content1);
    entityManager.persist(content2);

    review1 = Review.create(content1, author, "리뷰 내용1", 5.0);
    review2 = Review.create(content1, user1, "리뷰 내용2", 3.0);
    review3 = Review.create(content2, user2, "리뷰 내용3", 4.0);
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

  @Nested
  @DisplayName("리뷰 목록 조회")
  class FindAll {

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 기본 조건")
    void findAll_success() {
      // given

      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);

      entityManager.flush();
      entityManager.clear();

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          ReviewSortBy.CREATED_AT
      );

      // when
      List<Review> result = reviewRepository.findAll(condition);

      // then
      assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 콘텐츠ID 조건")
    void findAll_success_contentId() {
      // given

      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);

      entityManager.flush();
      entityManager.clear();

      // content1에 대한 리뷰 목록 조회
      ReviewQueryCondition condition = new ReviewQueryCondition(
          content1.getId(),
          null,
          null,
          10,
          SortDirection.ASCENDING,
          ReviewSortBy.CREATED_AT
      );

      // when
      List<Review> result = reviewRepository.findAll(condition);

      // then
      assertThat(result).hasSize(2)
          .extracting(Review::getId)
          .containsExactlyInAnyOrder(review1.getId(), review2.getId());
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 커서 페이지네이션 - 생성순 내림차순")
    void findAll_success_cursor_createdAt() {
      // given
      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);

      entityManager.flush();
      entityManager.clear();

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          10,
          SortDirection.DESCENDING,
          ReviewSortBy.CREATED_AT
      );

      // when
      List<Review> result = reviewRepository.findAll(condition);

      // then
      // 생성순 내림차순으로 정렬 되었는지 검증
      assertThat(result).extracting(Review::getCreatedAt)
          .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 커서 페이지네이션 - 별점순 내림차순")
    void findAll_success_cursor_rating() {
      // given

      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      // review1 별점 : 5.0, review2 별점 : 3.0, review3 별점 : 4.0
      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);

      entityManager.flush();
      entityManager.clear();

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          10,
          SortDirection.DESCENDING,
          ReviewSortBy.RATING
      );

      // when
      List<Review> result = reviewRepository.findAll(condition);

      // then
      // 별점순 내림차순으로 정렬 되었는지 검증
      assertThat(result).extracting(Review::getRating)
          .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 커서 페이지네이션 - 별점순 오름차순")
    void findAll_success_cursor_rating_ASC() {
      // given

      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      // review1 별점 : 5.0, review2 별점 : 3.0, review3 별점 : 4.0
      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);

      entityManager.flush();
      entityManager.clear();

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          ReviewSortBy.RATING
      );

      // when
      List<Review> result = reviewRepository.findAll(condition);

      // then
      // 별점순 오름차순으로 정렬 되었는지 검증
      assertThat(result).extracting(Review::getRating)
          .isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 리뷰가 존재하지 않음")
    void findAll_success_empty() {
      // given

      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          ReviewSortBy.CREATED_AT
      );

      // when
      List<Review> result = reviewRepository.findAll(condition);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 같은 정렬값에서 idAfter 기준으로 정렬(tie-breaker 테스트)")
    void findAll_success_withCursorTieBreaker() {
      // given

      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      Review review4 = Review.create(content1, user2, "리뷰 내용", 5.0);

      // review1, review4 별점 : 5.0, review2 별점 : 3.0, review3 별점 : 4.0
      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);
      entityManager.persist(review4);

      entityManager.flush();
      entityManager.clear();

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          10,
          SortDirection.DESCENDING,
          ReviewSortBy.RATING
      );

      // when
      List<Review> result = reviewRepository.findAll(condition);

      // then
      // review1, review4를 id 순으로 정렬
      List<UUID> sameRatingIds = Stream.of(review1, review4)
          .map(Review::getId)
          .sorted(Comparator.comparing(UUID::toString)) // 만약 DESC였다면 뒤에 .reversed()를 붙여야 함
          .toList();

      assertThat(result)
          .extracting(Review::getId)
          .containsExactly(
              sameRatingIds.get(0),
              sameRatingIds.get(1),
              review3.getId(),
              review2.getId()
          );
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 다음 페이지 데이터 존재 여부 확인")
    void findAll_success_limitPlusOne() {
      // given

      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      Review review4 = Review.create(content1, user2, "리뷰 내용", 5.0);

      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);
      entityManager.persist(review4);

      entityManager.flush();
      entityManager.clear();

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          2,
          SortDirection.ASCENDING,
          ReviewSortBy.CREATED_AT
      );

      // when
      List<Review> result = reviewRepository.findAll(condition);

      // then
      // limit + 1로 실제는 3개가 조회됨
      assertThat(result).hasSize(2 + 1);
    }
  }

  @Nested
  @DisplayName("리뷰 개수 조회")
  class Count {

    @Test
    @DisplayName("리뷰 개수 조회 성공 - 전체 리뷰 개수 조회")
    void count_success() {
      // given

      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);

      entityManager.flush();
      entityManager.clear();

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          ReviewSortBy.CREATED_AT
      );

      // when
      long result = reviewRepository.count(condition);

      // then
      assertThat(result).isEqualTo(3);
    }

    @Test
    @DisplayName("리뷰 개수 조회 성공 - 콘텐츠ID 조건에 맞는 리뷰 개수 조회")
    void count_success_contentId() {
      // given

      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      // review1, review2는 content1에 대한 리뷰, review3는 content2에 대한 리뷰
      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);

      entityManager.flush();
      entityManager.clear();

      // content2에 대한 리뷰만 조회
      ReviewQueryCondition condition = new ReviewQueryCondition(
          content2.getId(),
          null,
          null,
          10,
          SortDirection.ASCENDING,
          ReviewSortBy.CREATED_AT
      );

      // when
      long result = reviewRepository.count(condition);

      // then
      // content
      assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("리뷰 개수 조회 성공 - 조건에 맞는 리뷰가 없는 경우")
    void count_success_empty() {
      // given
      Content content3 = new Content(
          ContentType.MOVIE,
          "test3",
          "test 콘텐츠",
          "test/image.png",
          List.of("액션")
      );

      entityManager.persist(content3);

      // BeforeEach에서 content1, content2 저장 및 review1, review2, review3 초기화

      entityManager.persist(review1);
      entityManager.persist(review2);
      entityManager.persist(review3);

      entityManager.flush();
      entityManager.clear();

      ReviewQueryCondition condition = new ReviewQueryCondition(
          content3.getId(),
          null,
          null,
          10,
          SortDirection.ASCENDING,
          ReviewSortBy.CREATED_AT
      );

      // when
      long result = reviewRepository.count(condition);

      // then
      assertThat(result).isZero();
    }

  }

}
