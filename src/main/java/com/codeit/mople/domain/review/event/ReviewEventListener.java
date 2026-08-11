package com.codeit.mople.domain.review.event;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.review.repository.ReviewRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewEventListener {

  private final ReviewRepository reviewRepository;
  private final ContentRepository contentRepository;

  // TODO 김명근: 추후 Kafka 도입하여 Topic 만들어서 비동기로 처리를 통해 동시성 문제 해결
  // 현재로서는 데이터 정합성을 생각하여 @TransactionEventListener 미사용
  // 기본 전파레벨은 REQUIRED(트랜잭션 내에서 이 메서드가 호출되면 트랜잭션 새로 생성하지 않고 그 트랜잭션에 참여)
  @EventListener
  @Transactional
  public void handle(ReviewCreatedEvent event) {

    Content content = contentRepository.findById(event.contentId()).orElseThrow(() ->
        new ContentException(
            ContentErrorCode.CONTENT_NOT_FOUND,
            Map.of("contentId", event.contentId())
        )
    );

    long reviewCount = reviewRepository.countByContentId(content.getId());
    Double averageRating = reviewRepository.findAverageRatingByContentId(content.getId());

    content.updateRatingStats(averageRating, (int) reviewCount);

    log.info("리뷰 생성 후 콘텐츠 통계 업데이트 완료: contentId={}, averageRating={}, reviewCount={}",
        content.getId(), averageRating, reviewCount);
  }

  @EventListener
  @Transactional
  public void handle(ReviewUpdatedEvent event) {
    Content content = contentRepository.findById(event.contentId()).orElseThrow(() ->
        new ContentException(
            ContentErrorCode.CONTENT_NOT_FOUND,
            Map.of("contentId", event.contentId())
        )
    );

    Double averageRating = reviewRepository.findAverageRatingByContentId(content.getId());

    content.updateRatingStats(averageRating, content.getReviewCount());

    log.info("리뷰 수정 후 콘텐츠 통계 업데이트 완료: contentId={}, averageRating={}",
        content.getId(), averageRating);
  }

  @EventListener
  @Transactional
  public void handle(ReviewDeletedEvent event) {
    Content content = contentRepository.findById(event.contentId()).orElseThrow(() ->
        new ContentException(
            ContentErrorCode.CONTENT_NOT_FOUND,
            Map.of("contentId", event.contentId())
        )
    );

    long reviewCount = reviewRepository.countByContentId(content.getId());
    Double averageRating = reviewRepository.findAverageRatingByContentId(content.getId());

    Double updateAverageRating = reviewCount == 0 ? 0.0 : averageRating;

    // 리뷰 삭제 후 리뷰가 0개일 때 평균 평점을 0점으로(averageRating null 방지)
    content.updateRatingStats(
        updateAverageRating,
        (int) reviewCount
    );

    log.info("리뷰 삭제 후 콘텐츠 통계 업데이트 완료: contentId={}, averageRating={}, reviewCount={}",
        content.getId(), updateAverageRating, reviewCount);
  }


}
