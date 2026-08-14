package com.codeit.mople.domain.review.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.global.event.processed.ProcessedEventRepository;
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
public class ReviewConsumerTest {

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ProcessedEventRepository processedEventRepository;

  @InjectMocks
  private ReviewEventConsumer eventConsumer;

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

      // BeforeEach에서 contentId를 초기화

      UUID eventId = UUID.randomUUID();

      ReviewCreatedEvent event = new ReviewCreatedEvent(eventId, contentId, 4.0);

      given(processedEventRepository.insertIfAbsent(eventId))
          .willReturn(1);

      // when
      eventConsumer.handle(event);

      // then
      verify(processedEventRepository).insertIfAbsent(eventId);
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

      // BeforeEach에서 contentId를 초기화

      UUID eventId = UUID.randomUUID();

      ReviewUpdatedEvent event = new ReviewUpdatedEvent(eventId, contentId, 4.0, 5.0);

      given(processedEventRepository.insertIfAbsent(eventId))
          .willReturn(1);

      // when
      eventConsumer.handle(event);

      // then
      verify(processedEventRepository).insertIfAbsent(eventId);
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

      // BeforeEach에서 contentId를 초기화

      UUID eventId = UUID.randomUUID();

      ReviewDeletedEvent event = new ReviewDeletedEvent(eventId, contentId, 4.0);

      given(processedEventRepository.insertIfAbsent(eventId))
          .willReturn(1);

      // when
      eventConsumer.handle(event);

      // then
      verify(processedEventRepository).insertIfAbsent(eventId);
      verify(contentRepository).decreaseRating(contentId, 4.0);
    }

  }

}
