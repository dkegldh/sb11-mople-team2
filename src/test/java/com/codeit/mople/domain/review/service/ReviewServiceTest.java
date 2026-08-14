package com.codeit.mople.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition.ReviewSortBy;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition.SortDirection;
import com.codeit.mople.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mople.domain.review.dto.response.ReviewCursorResponse;
import com.codeit.mople.domain.review.dto.response.ReviewResponse;
import com.codeit.mople.domain.review.entity.Review;
import com.codeit.mople.domain.review.event.ReviewCreatedEvent;
import com.codeit.mople.domain.review.event.ReviewDeletedEvent;
import com.codeit.mople.domain.review.event.ReviewUpdatedEvent;
import com.codeit.mople.domain.review.event.ReviewWrittenEvent;
import com.codeit.mople.domain.review.exception.ReviewErrorCode;
import com.codeit.mople.domain.review.exception.ReviewException;
import com.codeit.mople.domain.review.repository.ReviewRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.dto.UserSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ReviewServiceTest {

  @Mock
  private ReviewRepository reviewRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private ReviewService reviewService;

  private UUID authorId;
  private UUID contentId;
  private User author;
  private Content content;
  private String reviewText;
  private Double reviewRating;
  private ReviewCreateRequest createRequest;

  private UUID review1Id;
  private UUID reviewId;
  private Review review1;
  private Review review2;
  private Review review3;
  private Review review4;
  private ReviewUpdateRequest updateRequest;

  @BeforeEach
  void setUp() {
    authorId = UUID.randomUUID();
    contentId = UUID.randomUUID();
    author = mock(User.class);
    content = mock(Content.class);

    reviewText = "리뷰 내용";
    reviewRating = 5.0;
    createRequest = new ReviewCreateRequest(contentId, reviewText, reviewRating);

    reviewId = UUID.randomUUID();
    review1Id = UUID.randomUUID();
    review1 = Review.create(content, author, reviewText, reviewRating);
    ReflectionTestUtils.setField(review1, "id", review1Id);

    updateRequest = new ReviewUpdateRequest("수정된 내용", 3.0);
  }

  @Nested
  @DisplayName("리뷰 생성")
  class Create {

    @Test
    @DisplayName("리뷰 생성 성공")
    void create_success() {
      // given

      // BeforeEach에서 author, authorId, content, contentId, Review Create Request 초기화

      // Content
      given(content.getId())
          .willReturn(contentId);

      // UserSummary
      given(author.getId())
          .willReturn(authorId);
      given(author.getName())
          .willReturn("test");
      given(author.getProfileImageUrl())
          .willReturn("profile.png");

      Review review = Review.create(content, author, createRequest.text(), createRequest.rating());
      ReflectionTestUtils.setField(review, "id", reviewId);

      ReviewResponse response = ReviewResponse.from(review);

      // User 조회 → Content 조회 → Review 저장 순
      given(userRepository.findById(authorId))
          .willReturn(Optional.of(author));

      given(contentRepository.findById(contentId))
          .willReturn(Optional.of(content));

      // content는 mock 객체이고 서비스 코드에서 content.getId()를 사용하기 때문에 Id를 Stub 해줘야 함
      given(content.getId()).willReturn(contentId);

      given(reviewRepository.save(any(Review.class)))
          .willReturn(review);

      // when
      ReviewResponse result = reviewService.create(authorId, createRequest);

      // then
      assertThat(result).isEqualTo(response);
      assertThat(review.getId()).isEqualTo(reviewId);

      verify(userRepository).findById(authorId);
      verify(contentRepository).findById(contentId);
      verify(reviewRepository).save(any(Review.class));

      verify(eventPublisher).publishEvent(new ReviewWrittenEvent(authorId, "test"));

      verify(eventPublisher).publishEvent(
          argThat((Object event) ->
              event instanceof ReviewCreatedEvent createdEvent
                  && createdEvent.eventId() != null
                  && createdEvent.contentId().equals(contentId)
                  && createdEvent.rating() == reviewRating
          )
      );
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 사용자가 존재하지 않음")
    void create_fail_notFoundUser() {
      // given

      // BeforeEach에서 authorId 초기화

      given(userRepository.findById(authorId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
          reviewService.create(authorId, createRequest))
          .isInstanceOf(UserException.class)
          .extracting("errorCode")
          .isEqualTo(UserErrorCode.USER_NOT_FOUND);

      verify(userRepository).findById(authorId);
      verifyNoInteractions(contentRepository, reviewRepository);
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 콘텐츠가 존재하지 않음")
    void create_fail_notFoundContent() {
      // given

      // BeforeEach에서 authorId 초기화

      given(userRepository.findById(authorId))
          .willReturn(Optional.of(author));

      given(contentRepository.findById(contentId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
          reviewService.create(authorId, createRequest))
          .isInstanceOf(ContentException.class)
          .extracting("errorCode")
          .isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND);

      verify(userRepository).findById(authorId);
      verify(contentRepository).findById(contentId);
      verifyNoInteractions(reviewRepository);
    }

  }

  @Nested
  @DisplayName("리뷰 목록 조회")
  class FindAll {

    @BeforeEach
    void setUp() {
      // BeforeEach에서 review1 초기화(별점 3점)
      // 실제 DB에 저장되지 않기 때문에 같은 content, author mock 객체로 사용
      review2 = Review.create(content, author, "리뷰 내용 2", 5.0);
      review3 = Review.create(content, author, "리뷰 내용 3", 2.0);
      review4 = Review.create(content, author, "리뷰 내용 4", 5.0);

      ReflectionTestUtils.setField(review2, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(review3, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(review4, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 기본 조건")
    void findAll_success() {
      // given

      // BeforeEach에서 review1, review2, review3, review4 초기화

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          10,
          SortDirection.DESCENDING,
          ReviewSortBy.CREATED_AT
      );

      given(reviewRepository.findAll(condition))
          .willReturn(java.util.List.of(review1, review2, review3, review4));

      given(reviewRepository.count(condition))
          .willReturn(4L);

      // Content
      given(content.getId())
          .willReturn(contentId);

      // UserSummary
      given(author.getId())
          .willReturn(authorId);
      given(author.getName())
          .willReturn("test");
      given(author.getProfileImageUrl())
          .willReturn("profile.png");

      // when
      ReviewCursorResponse result = reviewService.findAll(condition);

      // then
      assertThat(result.data()).hasSize(4);
      assertThat(result.totalCount()).isEqualTo(4L);
      assertThat(result.hasNext()).isFalse();

      assertThat(result.data())
          .extracting(ReviewResponse::text)
          .containsExactlyInAnyOrder(
              review1.getText(),
              review2.getText(),
              review3.getText(),
              review4.getText()
          );

      verify(reviewRepository).findAll(condition);
      verify(reviewRepository).count(condition);
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 다음 페이지 존재")
    void findAll_success_hasNext() {
      // given

      // BeforeEach에서 review1, review2, review3, review4 초기화

      // cursor, idAfter에 review4에 대한 정보가 와야함
      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          2,
          SortDirection.DESCENDING,
          ReviewSortBy.RATING
      );

      // review2, review4 별점 : 5점, review1 별점 : 3점, review3 별점 : 2점
      // limit + 1까지 조회하기 때문에 review1 포함
      given(reviewRepository.findAll(condition))
          .willReturn(List.of(review2, review4, review1));

      given(reviewRepository.count(condition))
          .willReturn(4L);

      // Content
      given(content.getId())
          .willReturn(contentId);

      // UserSummary
      given(author.getId())
          .willReturn(authorId);
      given(author.getName())
          .willReturn("test");
      given(author.getProfileImageUrl())
          .willReturn("profile.png");

      // when
      ReviewCursorResponse result = reviewService.findAll(condition);

      // then
      assertThat(result.data()).hasSize(2);
      assertThat(result.totalCount()).isEqualTo(4L);
      assertThat(result.hasNext()).isTrue();

      // nextCursor, nextIdAfter
      assertThat(result.nextCursor()).isEqualTo(String.valueOf(review4.getRating()));
      assertThat(result.nextIdAfter()).isEqualTo(review4.getId());

      verify(reviewRepository).findAll(condition);

      // cursor 존재로 인해 totalCount
      verify(reviewRepository).count(condition);
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 마지막 페이지")
    void findAll_success_lastPage() {
      // given

      // BeforeEach에서 review1, review2, review3, review4 초기화

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          10,
          SortDirection.DESCENDING,
          ReviewSortBy.RATING
      );

      given(reviewRepository.findAll(condition))
          .willReturn(List.of(review2, review4, review1, review3));

      given(reviewRepository.count(condition))
          .willReturn(4L);

      // Content
      given(content.getId())
          .willReturn(contentId);

      // UserSummary
      given(author.getId())
          .willReturn(authorId);
      given(author.getName())
          .willReturn("test");
      given(author.getProfileImageUrl())
          .willReturn("profile.png");

      // when
      ReviewCursorResponse result = reviewService.findAll(condition);

      // then
      assertThat(result.data()).hasSize(4);
      assertThat(result.totalCount()).isEqualTo(4L);
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
      assertThat(result.nextIdAfter()).isNull();

      verify(reviewRepository).findAll(condition);
      verify(reviewRepository).count(condition);
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공 - 조회 결과 없음")
    void findAll_success_empty() {
      // given

      // BeforeEach에서 review1, review2, review3, review4 초기화

      ReviewQueryCondition condition = new ReviewQueryCondition(
          null,
          null,
          null,
          10,
          SortDirection.DESCENDING,
          ReviewSortBy.RATING
      );

      given(reviewRepository.findAll(condition))
          .willReturn(List.of());

      given(reviewRepository.count(condition))
          .willReturn(0L);

      // when
      ReviewCursorResponse result = reviewService.findAll(condition);

      // then
      assertThat(result.data()).isEmpty();
      assertThat(result.totalCount()).isEqualTo(0L);
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
      assertThat(result.nextIdAfter()).isNull();

      verify(reviewRepository).findAll(condition);
      verify(reviewRepository).count(condition);
    }

  }

  @Nested
  @DisplayName("리뷰 수정")
  class Update {

    @Test
    @DisplayName("리뷰 수정 성공")
    void update_success() {
      // given

      // BeforeEach에서 reviewId, review, authorId, contentId, updateRequest 초기화

      // Content
      given(content.getId())
          .willReturn(contentId);

      // UserSummary
      given(author.getId())
          .willReturn(authorId);
      given(author.getName())
          .willReturn("test");
      given(author.getProfileImageUrl())
          .willReturn("profile.png");

      ReviewResponse response = new ReviewResponse(
          review1.getId(),
          contentId,
          new UserSummary(authorId, "test", "profile.png"),
          updateRequest.text(),
          updateRequest.rating()
      );

      given(reviewRepository.findById(review1Id))
          .willReturn(Optional.of(review1));

      // when
      ReviewResponse result = reviewService.update(review1Id, updateRequest, authorId);

      // then
      assertThat(result).isEqualTo(response);
      assertThat(review1.getText()).isEqualTo(updateRequest.text());
      assertThat(review1.getRating()).isEqualTo(updateRequest.rating());

      verify(reviewRepository).findById(review1Id);

      verify(eventPublisher).publishEvent(
          argThat((Object event) ->
              event instanceof ReviewUpdatedEvent updatedEvent
                  && updatedEvent.eventId() != null
                  && updatedEvent.contentId().equals(contentId)
                  && updatedEvent.oldRating() == 5.0
                  && updatedEvent.newRating() == 3.0
          )
      );
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 리뷰가 존재하지 않음")
    void update_fail_notFoundReview() {
      // given

      // BeforeEach에서 reviewId, authorId, updateRequest 초기화

      given(reviewRepository.findById(review1Id))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
          reviewService.update(review1Id, updateRequest, authorId)
      )
          .isInstanceOf(ReviewException.class)
          .extracting("errorCode")
          .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND);

      verify(reviewRepository).findById(review1Id);

      verifyNoInteractions(author, content);
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 리뷰 작성자가 아님")
    void update_fail_forbidden() {
      // given
      UUID noAuthorId = UUID.randomUUID();

      // BeforeEach에서 reviewId, authorId, updateRequest를 초기화

      given(reviewRepository.findById(review1Id))
          .willReturn(Optional.of(review1));

      given(author.getId())
          .willReturn(authorId);

      // when & then
      assertThatThrownBy(() ->
          reviewService.update(review1Id, updateRequest, noAuthorId)
      )
          .isInstanceOf(ReviewException.class)
          .extracting("errorCode")
          .isEqualTo(ReviewErrorCode.REVIEW_FORBIDDEN);
    }

  }

  @Nested
  @DisplayName("리뷰 삭제")
  class Delete {

    @Test
    @DisplayName("리뷰 삭제 성공")
    void delete_success() {
      // given

      // BeforeEach에서 review, reviewId, authorId 초기화

      given(reviewRepository.findById(review1Id))
          .willReturn(Optional.of(review1));

      given(author.getId())
          .willReturn(authorId);

      given(content.getId())
          .willReturn(contentId);
      // reviewRepository.delete() 메서드는 void이기 때문에 값을 반환하지 않음

      // when
      reviewService.delete(review1Id, authorId);

      // then
      verify(reviewRepository).findById(review1Id);
      verify(reviewRepository).delete(review1);

      verify(eventPublisher).publishEvent(
          argThat((Object event) ->
              event instanceof ReviewDeletedEvent deletedEvent
                  && deletedEvent.eventId() != null
                  && deletedEvent.contentId().equals(contentId)
                  && deletedEvent.rating() == reviewRating
          )
      );
    }

    @Test
    @DisplayName("리뷰 삭제 실패 - 리뷰가 존재하지 않음")
    void delete_fail_notFoundReview() {
      // given
      given(reviewRepository.findById(review1Id))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
          reviewService.delete(review1Id, authorId)
      )
          .isInstanceOf(ReviewException.class)
          .extracting("errorCode")
          .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND);

      verify(reviewRepository).findById(review1Id);

      verify(reviewRepository, never()).delete(any(Review.class));
      verifyNoInteractions(content);
    }

    @Test
    @DisplayName("리뷰 삭제 실패 - 리뷰 작성자가 아님")
    void delete_fail_forbidden() {
      // given
      UUID noAuthorId = UUID.randomUUID();

      given(reviewRepository.findById(review1Id))
          .willReturn(Optional.of(review1));

      given(author.getId())
          .willReturn(authorId);

      // when & then
      assertThatThrownBy(() ->
          reviewService.delete(review1Id, noAuthorId)
      )
          .isInstanceOf(ReviewException.class)
          .extracting("errorCode")
          .isEqualTo(ReviewErrorCode.REVIEW_FORBIDDEN);

      verify(reviewRepository).findById(review1Id);
      verify(author).getId();

      verify(reviewRepository, never()).delete(any(Review.class));
      verifyNoInteractions(content);
    }

  }

}
