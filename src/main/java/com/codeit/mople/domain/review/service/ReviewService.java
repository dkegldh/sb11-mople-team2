package com.codeit.mople.domain.review.service;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition.ReviewSortBy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final UserRepository userRepository;
  private final ContentRepository contentRepository;
  private final ApplicationEventPublisher publisher;

  @Transactional
  public ReviewResponse create(UUID authorId, ReviewCreateRequest request) {

    log.debug("리뷰 생성 시도: authorId={}, contentId={}, rating={}",
        authorId, request.contentId(), request.rating());

    User author = userRepository.findById(authorId).orElseThrow(() ->
        new UserException(
            UserErrorCode.USER_NOT_FOUND,
            Map.of("userId", authorId)
        )
    );

    Content content = contentRepository.findById(request.contentId()).orElseThrow(() ->
        new ContentException(
            ContentErrorCode.CONTENT_NOT_FOUND,
            Map.of("contentId", request.contentId())
        )
    );

    Review review = Review.create(content, author, request.text(), request.rating());

    Review savedReview = reviewRepository.save(review);

    publisher.publishEvent(new ReviewWrittenEvent(authorId, author.getName()));

    publisher.publishEvent(new ReviewCreatedEvent(content.getId()));

    ReviewResponse response = ReviewResponse.from(savedReview);
    log.info("리뷰 생성 완료: reviewId={}, authorId={}, contentId={}",
        savedReview.getId(), authorId, request.contentId());

    return response;
  }

  @Transactional(readOnly = true)
  public ReviewCursorResponse findAll(ReviewQueryCondition condition) {

    log.debug("리뷰 목록 조회 시도: contentId={}, cursor={}, idAfter={},"
            + "limit={}, sortBy={}, sortDirection={}",
        condition.contentId(),
        condition.cursor(),
        condition.idAfter(),
        condition.limit(),
        condition.sortBy(),
        condition.sortDirection()
    );

    // limit + 1만큼 조회
    List<Review> reviews = reviewRepository.findAll(condition);

    // 다음 페이지 존재 여부 확인
    boolean hasNext = reviews.size() > condition.limit();

    // 응답용 데이터만 limit 개수 유지
    List<Review> pageReviews = hasNext ?
        new ArrayList<>(reviews.subList(0, condition.limit())) : reviews;

    List<ReviewResponse> data = pageReviews.stream()
        .map(ReviewResponse::from)
        .toList();

    long totalCount = reviewRepository.count(condition);

    // 임시 커서, 보조 커서 초기화
    String nextCursor = null;
    UUID nextIdAfter = null;

    // 마지막 원소 조회
    if (hasNext && !pageReviews.isEmpty()) {
      Review lastReview = pageReviews.get(pageReviews.size() - 1);

      nextIdAfter = lastReview.getId();

      // 정렬 조건
      if (condition.sortBy() == ReviewSortBy.CREATED_AT) {
        nextCursor = lastReview.getCreatedAt().toString();
      } else if (condition.sortBy() == ReviewSortBy.RATING) {
        nextCursor = String.valueOf(lastReview.getRating());
      }
    }

    log.info("리뷰 목록 조회 완료: size={}, totalCount={}, hasNext={}, nextCursor={}, nextIdAfter={}",
        data.size(), totalCount, hasNext, nextCursor, nextIdAfter);

    return new ReviewCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        condition.sortBy(),
        condition.sortDirection()
    );
  }

  @Transactional
  public ReviewResponse update(UUID reviewId, ReviewUpdateRequest request, UUID requesterId) {

    log.debug("리뷰 수정 시도: reviewId={}, requesterId={}, rating={}",
        reviewId, requesterId, request.rating());

    Review review = reviewRepository.findById(reviewId).orElseThrow(() ->
        new ReviewException(
            ReviewErrorCode.REVIEW_NOT_FOUND,
            Map.of("reviewId", reviewId)
        )
    );

    validateRequesterIsAuthor(review, requesterId);

    if (request.text() != null) {
      review.updateText(request.text());
    }

    if (request.rating() != null) {
      review.updateRating(request.rating());
    }

    Content content = review.getContent();

    // 콘텐츠의 평균 평점을 조회
    // 리뷰 내용만 변경 된 경우 계산하지 않음
    if (request.rating() != null) {
      publisher.publishEvent(new ReviewUpdatedEvent(content.getId()));
    }

    ReviewResponse response = ReviewResponse.from(review);

    log.info("리뷰 수정 완료: reviewId={}, requesterId={}, contentId={}, rating={}",
        reviewId, requesterId, content.getId(), request.rating());

    return response;
  }

  @Transactional
  public void delete(UUID reviewId, UUID requesterId) {

    log.debug("리뷰 삭제 시도: reviewId={}, requesterId={}",
        reviewId, requesterId);

    Review review = reviewRepository.findById(reviewId).orElseThrow(() ->
        new ReviewException(
            ReviewErrorCode.REVIEW_NOT_FOUND,
            Map.of("reviewId", reviewId)
        )
    );

    validateRequesterIsAuthor(review, requesterId);

    Content content = review.getContent();

    reviewRepository.delete(review);

    publisher.publishEvent(new ReviewDeletedEvent(content.getId()));

    log.info(
        "리뷰 삭제 완료: reviewId={}, requesterId={}, contentId={}",
        reviewId, requesterId, content.getId());
  }

  private void validateRequesterIsAuthor(Review review, UUID requesterId) {
    UUID authorId = review.getAuthor().getId();
    if (!authorId.equals(requesterId)) {
      throw new ReviewException(
          ReviewErrorCode.REVIEW_FORBIDDEN,
          Map.of("authorId", authorId, "requesterId", requesterId)
      );
    }
  }

}
