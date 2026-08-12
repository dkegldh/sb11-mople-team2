package com.codeit.mople.domain.review.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.review.repository.ReviewRepository;
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

@ExtendWith(MockitoExtension.class)
public class ReviewListenerTest {

  @Mock
  private ReviewRepository reviewRepository;

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private Content content;

  @InjectMocks
  private ReviewEventListener eventListener;

  private UUID contentId;

  @BeforeEach
  void setUp() {
    contentId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("리뷰 생성 이벤트(콘텐츠 개수, 평균 평점 업데이트)")
  class CreatedEvent {

    @Test
    @DisplayName("리뷰 생성 이벤트 성공")
    void handle_success() {
      // given
      ReviewCreatedEvent event = new ReviewCreatedEvent(contentId);

      given(contentRepository.findById(contentId))
          .willReturn(Optional.of(content));

      given(content.getId())
          .willReturn(contentId);

      // 이벤트 발행 전 리뷰가 이미 생성된 상태를 가정
      given(reviewRepository.countByContentId(contentId))
          .willReturn(1L);

      given(reviewRepository.findAverageRatingByContentId(contentId))
          .willReturn(4.5);

      // when
      eventListener.handle(event);

      // then
      verify(contentRepository).findById(contentId);
      verify(reviewRepository).countByContentId(contentId);
      verify(reviewRepository).findAverageRatingByContentId(contentId);

      verify(content).updateRatingStats(4.5, 1);
    }

    @Test
    @DisplayName("리뷰 생성 이벤트 실패 - 콘텐츠가 존재하지 않음")
    void handle_fail_contentNotFound() {
      // given
      UUID contentId = UUID.randomUUID();

      ReviewCreatedEvent event = new ReviewCreatedEvent(contentId);

      given(contentRepository.findById(contentId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> eventListener.handle(event))
          .isInstanceOf(ContentException.class);

      verify(contentRepository).findById(contentId);

      verifyNoInteractions(reviewRepository);
    }

  }

  @Nested
  @DisplayName("리뷰 수정 이벤트(콘텐츠 평균 평점 업데이트)")
  class UpdatedEvent {

    @Test
    @DisplayName("리뷰 수정 이벤트 성공")
    void handle_success() {
      // given
      ReviewUpdatedEvent event = new ReviewUpdatedEvent(contentId);

      given(contentRepository.findById(contentId))
          .willReturn(Optional.of(content));

      given(content.getId())
          .willReturn(contentId);

      given(content.getReviewCount())
          .willReturn(20);

      given(reviewRepository.findAverageRatingByContentId(contentId))
          .willReturn(3.8);

      // when
      eventListener.handle(event);

      // then
      verify(contentRepository).findById(contentId);
      verify(reviewRepository).findAverageRatingByContentId(contentId);

      verify(content).updateRatingStats(3.8, 20);
    }

    @Test
    @DisplayName("리뷰 수정 이벤트 실패 - 콘텐츠가 존재하지 않음")
    void handle_fail_contentNotFound() {
      // given
      UUID contentId = UUID.randomUUID();

      ReviewUpdatedEvent event = new ReviewUpdatedEvent(contentId);

      given(contentRepository.findById(contentId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> eventListener.handle(event))
          .isInstanceOf(ContentException.class);

      verify(contentRepository).findById(contentId);

      verifyNoInteractions(reviewRepository);
    }

  }


  @Nested
  @DisplayName("리뷰 삭제 이벤트(콘텐츠 개수, 평균 평점 업데이트)")
  class ReviewDeleted {

    @Test
    @DisplayName("리뷰 삭제 이벤트 성공")
    void handle_success() {
      // given
      ReviewDeletedEvent event = new ReviewDeletedEvent(contentId);

      given(contentRepository.findById(contentId))
          .willReturn(Optional.of(content));

      given(content.getId())
          .willReturn(contentId);

      // 이벤트 발행 전 리뷰가 이미 삭제된 상태를 가정
      given(reviewRepository.countByContentId(contentId))
          .willReturn(0L);

      given(reviewRepository.findAverageRatingByContentId(contentId))
          .willReturn(null);

      // when
      eventListener.handle(event);

      // then
      verify(contentRepository).findById(contentId);
      verify(reviewRepository).countByContentId(contentId);
      verify(reviewRepository).findAverageRatingByContentId(contentId);

      verify(content).updateRatingStats(0.0, 0);
    }

    @Test
    @DisplayName("리뷰 삭제 이벤트 실패 - 콘텐츠가 존재하지 않음")
    void handle_fail_contentNotFound() {
      // given
      UUID contentId = UUID.randomUUID();

      ReviewDeletedEvent event = new ReviewDeletedEvent(contentId);

      given(contentRepository.findById(contentId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> eventListener.handle(event))
          .isInstanceOf(ContentException.class);

      verify(contentRepository).findById(contentId);

      verifyNoInteractions(reviewRepository);
    }

  }

}
