package com.codeit.mople.domain.review.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.review.entity.Review;
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
  private ContentRepository contentRepository;

  @InjectMocks
  private ReviewEventListener eventListener;

  private UUID contentId;
  private UUID reviewId;
  private Review review;

  @BeforeEach
  void setUp() {
    contentId = UUID.randomUUID();
    reviewId = UUID.randomUUID();
    review = mock(Review.class);
  }

  @Nested
  @DisplayName("리뷰 생성 이벤트(콘텐츠 개수, 평균 평점 업데이트)")
  class CreatedEvent {

    @Test
    @DisplayName("리뷰 생성 이벤트 성공")
    void handle_success() {
      // given
      ReviewCreatedEvent event = new ReviewCreatedEvent(contentId, 4.0);

      // when
      eventListener.handle(event);

      // then
      verify(contentRepository).increaseRating(contentId, 4.0);
    }

  }

  @Nested
  @DisplayName("리뷰 수정 이벤트(콘텐츠 평균 평점 업데이트)")
  class UpdatedEvent {

    @Test
    @DisplayName("리뷰 수정 이벤트 성공")
    void handle_success() {
      // given
      ReviewUpdatedEvent event = new ReviewUpdatedEvent(contentId, 4.0, 5.0);

      // when
      eventListener.handle(event);

      // then
      verify(contentRepository).updateRating(contentId, 4.0, 5.0);
    }

  }


  @Nested
  @DisplayName("리뷰 삭제 이벤트(콘텐츠 개수, 평균 평점 업데이트)")
  class ReviewDeleted {

    @Test
    @DisplayName("리뷰 삭제 이벤트 성공")
    void handle_success() {
      // given
      ReviewDeletedEvent event = new ReviewDeletedEvent(contentId, 4.0);

      // when
      eventListener.handle(event);

      // then
      verify(contentRepository).decreaseRating(contentId, 4.0);
    }

  }

}
